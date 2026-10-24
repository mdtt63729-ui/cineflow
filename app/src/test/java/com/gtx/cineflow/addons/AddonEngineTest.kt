package com.gtx.cineflow.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonEngineTest {
    @Test fun resourceParserAcceptsStringAndObjectFormsAndSkipsMalformedEntries() {
        val refs = ResourceRefSerializer.parseJson("""["catalog", {"name":"meta","types":["movie"],"idPrefixes":["tt"]}, 4, {}, null]""")
        assertEquals(listOf("catalog", "meta"), refs.map { it.name })
        assertEquals(listOf("movie"), refs.last().types)
        assertEquals(listOf("tt"), refs.last().idPrefixes)
    }

    @Test fun manifestDeserializerSupportsProtocolResourceUnion() {
        val manifest = AddonJson.gson.fromJson(
            """{"id":"org.example.test","name":"Test Addon","version":"1.0.0","resources":["catalog",{"name":"meta","types":["movie"]}],"catalogs":[{"type":"movie","id":"popular","extra":[{"name":"search"}]}]}""",
            AddonManifest::class.java
        )
        assertEquals(2, manifest.resources.size)
        assertTrue(manifest.catalogs.single().supportsSearch)
    }

    @Test fun onlyHttpsAndLoopbackHttpAreAccepted() {
        assertNotNull(AddonClient.normalizeBaseUrl("https://addons.example.org/stremio"))
        assertNotNull(AddonClient.normalizeBaseUrl("http://127.0.0.1:7000"))
        assertNotNull(AddonClient.normalizeBaseUrl("http://localhost:7000"))
        assertNull(AddonClient.normalizeBaseUrl("http://addons.example.org"))
        assertNull(AddonClient.normalizeBaseUrl("file:///etc/passwd"))
        assertNull(AddonClient.normalizeBaseUrl("https://user:password@addons.example.org"))
    }

    @Test fun emptyIdPrefixesMeanUnrestricted() {
        val record = AddonRecord(
            baseUrl = "https://addons.example.org",
            manifest = AddonManifest(
                id = "org.example.addon", name = "Example",
                resources = listOf(ResourceRef("meta", types = listOf("movie")))
            )
        )
        assertTrue(AddonManager(AddonClient()).supportsResource(record, "meta", "movie", "tt123"))
        assertFalse(AddonManager(AddonClient()).supportsResource(record, "meta", "series", "tt123"))
    }
}
