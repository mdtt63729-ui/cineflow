package com.gtx.cineflow.addons.cloudstream

import com.google.gson.JsonParser
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * Safe, non-executing .cs3 archive inspection. CineFlow deliberately never loads or runs
 * bytecode from a third-party Cloudstream extension. Only small metadata-only archives
 * are eligible for metadata preview.
 */
class Cs3ArchiveInspector {
    fun inspect(file: File): Cs3InspectionResult {
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_ARCHIVE_BYTES) {
            return invalid("Archive is missing, empty, or larger than 50 MB.")
        }
        if (!file.name.endsWith(".cs3", ignoreCase = true)) {
            return invalid("Choose a .cs3 archive.")
        }

        return try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size > MAX_ENTRIES) return invalid("Archive contains too many entries.")
                if (entries.any { isUnsafePath(it.name) }) {
                    return invalid("Archive contains an unsafe path.")
                }

                val executable = entries.firstOrNull { isExecutable(it.name) }
                if (executable != null) {
                    return Cs3InspectionResult(
                        status = Cs3InspectionStatus.EXECUTABLE_PLUGIN_UNSUPPORTED,
                        entryCount = entries.size,
                        warnings = listOf("Executable content detected: ${executable.name.take(100)}"),
                        message = "This .cs3 contains executable plugin code. CineFlow will not load or execute third-party bytecode."
                    )
                }

                val metadataEntry = entries.firstOrNull {
                    !it.isDirectory && it.name.substringAfterLast('/').lowercase() in METADATA_NAMES
                } ?: return Cs3InspectionResult(
                    status = Cs3InspectionStatus.UNSUPPORTED_METADATA,
                    entryCount = entries.size,
                    message = "No supported metadata.json or plugin.json was found."
                )

                if (metadataEntry.size > MAX_METADATA_BYTES) return invalid("Metadata file exceeds 256 KB.")
                val metadata = zip.getInputStream(metadataEntry).use { stream ->
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        val root = JsonParser.parseReader(reader)
                        if (!root.isJsonObject) return invalid("Metadata must be a JSON object.")
                        val obj = root.asJsonObject
                        fun string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
                            obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                                ?.asString?.trim()?.takeIf { it.isNotEmpty() }
                        }
                        val name = string("name", "pluginName", "title")
                            ?: return invalid("Metadata is missing a plugin name.")
                        val authors = obj.get("authors")?.takeIf { it.isJsonArray }
                            ?.asJsonArray?.mapNotNull { element ->
                                if (element.isJsonPrimitive && element.asJsonPrimitive.isString) element.asString.take(100) else null
                            }?.take(20).orEmpty()
                        Cs3PluginMetadata(
                            name = name.take(120),
                            version = string("version", "pluginVersion")?.take(40),
                            description = string("description")?.take(1000),
                            authors = authors,
                            language = string("language")?.take(40),
                            repositoryUrl = string("repositoryUrl", "repo")?.take(300)
                        )
                    }
                }
                Cs3InspectionResult(
                    status = Cs3InspectionStatus.METADATA_ONLY_COMPATIBLE,
                    metadata = metadata,
                    entryCount = entries.size,
                    warnings = listOf("Metadata preview only; this does not install or run the Cloudstream plugin."),
                    message = "Metadata was read safely. Runtime compatibility is not provided."
                )
            }
        } catch (_: Exception) {
            invalid("Archive could not be read as a valid .cs3 ZIP file.")
        }
    }

    private fun invalid(message: String) = Cs3InspectionResult(
        status = Cs3InspectionStatus.INVALID_ARCHIVE,
        message = message
    )

    private fun isUnsafePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.startsWith('/') || normalized.split('/').any { it == ".." } ||
            normalized.contains('\u0000')
    }

    private fun isExecutable(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("dex", "class", "jar", "apk", "so", "dll", "exe")
    }

    companion object {
        private const val MAX_ARCHIVE_BYTES = 50L * 1024L * 1024L
        private const val MAX_METADATA_BYTES = 256L * 1024L
        private const val MAX_ENTRIES = 500
        private val METADATA_NAMES = setOf("metadata.json", "plugin.json")
    }
}
