package com.gtx.cineflow.addons.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Cs3ArchiveInspectorTest {
    @Test fun readsMetadataOnlyArchive() {
        val file = zip("safe.cs3", mapOf("metadata.json" to """{"name":"Example","version":"1.2","authors":["A"]}"""))
        val result = Cs3ArchiveInspector().inspect(file)
        assertEquals(Cs3InspectionStatus.METADATA_ONLY_COMPATIBLE, result.status)
        assertEquals("Example", result.metadata?.name)
        assertNotNull(result.metadata)
        file.delete()
    }

    @Test fun refusesExecutableArchive() {
        val file = zip("plugin.cs3", mapOf("metadata.json" to """{"name":"Plugin"}""", "classes.dex" to "bytecode"))
        val result = Cs3ArchiveInspector().inspect(file)
        assertEquals(Cs3InspectionStatus.EXECUTABLE_PLUGIN_UNSUPPORTED, result.status)
        assertTrue(result.message.contains("will not load"))
        file.delete()
    }

    @Test fun rejectsWrongExtension() {
        val file = zip("plugin.zip", mapOf("metadata.json" to """{"name":"Plugin"}"""))
        assertEquals(Cs3InspectionStatus.INVALID_ARCHIVE, Cs3ArchiveInspector().inspect(file).status)
        file.delete()
    }

    private fun zip(name: String, entries: Map<String, String>): File {
        val file = File.createTempFile("cineflow-test-", "-${name}")
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (entryName, value) ->
                out.putNextEntry(ZipEntry(entryName))
                out.write(value.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }
        return file
    }
}
