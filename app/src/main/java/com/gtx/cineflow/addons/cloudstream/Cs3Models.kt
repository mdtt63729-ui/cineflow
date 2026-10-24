package com.gtx.cineflow.addons.cloudstream

data class Cs3PluginMetadata(
    val name: String,
    val version: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val language: String? = null,
    val repositoryUrl: String? = null
)

enum class Cs3InspectionStatus {
    METADATA_ONLY_COMPATIBLE,
    EXECUTABLE_PLUGIN_UNSUPPORTED,
    INVALID_ARCHIVE,
    UNSUPPORTED_METADATA
}

data class Cs3InspectionResult(
    val status: Cs3InspectionStatus,
    val metadata: Cs3PluginMetadata? = null,
    val entryCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val message: String
)
