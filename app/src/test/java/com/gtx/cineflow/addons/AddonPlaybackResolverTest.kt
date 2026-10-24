package com.gtx.cineflow.addons

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonPlaybackResolverTest {
    private fun source(url: String?, hash: String? = null) = AddonSource(
        "https://addon.example", "Addon", if (hash != null) "torrent" else "direct",
        "Test source", url = url, infoHash = hash
    )

    @Test fun acceptsHttpsDirectStream() {
        val result = AddonPlaybackResolver.resolve(source("https://media.example/video.m3u8"))
        assertTrue(result is PlaybackResolution.Playable)
        assertEquals(MimeTypes.APPLICATION_M3U8, (result as PlaybackResolution.Playable).item.mimeType)
    }

    @Test fun acceptsLoopbackHttpOnly() {
        assertTrue(AddonPlaybackResolver.resolve(source("http://127.0.0.1:8080/stream")) is PlaybackResolution.Playable)
        assertTrue(AddonPlaybackResolver.resolve(source("http://media.example/stream")) is PlaybackResolution.Rejected)
    }

    @Test fun rejectsUnsafeSchemesAndCredentials() {
        assertTrue(AddonPlaybackResolver.resolve(source("file:///sdcard/movie.mp4")) is PlaybackResolution.Rejected)
        assertTrue(AddonPlaybackResolver.resolve(source("https://user:pass@media.example/movie.mp4")) is PlaybackResolution.Rejected)
    }

    @Test fun rejectsTorrentUntilP5() {
        assertTrue(AddonPlaybackResolver.resolve(source(null, "0123456789abcdef0123456789abcdef01234567")) is PlaybackResolution.Rejected)
    }
}
