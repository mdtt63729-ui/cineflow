package com.gtx.cineflow.addons

import org.junit.Assert.assertEquals
import org.junit.Test

class AddonSourceRankerTest {
    private fun source(name: String, url: String? = "https://example.org/video.mp4", hash: String? = null) =
        AddonSource("https://addon.example", "Test addon", if (hash != null) "torrent" else "direct",
            name, url = url, infoHash = hash)

    @Test fun directSourcesRankAheadOfTorrentDescriptors() {
        val torrent = source("2160p", url = null, hash = "0123456789abcdef0123456789abcdef01234567")
        val direct = source("720p")
        assertEquals(direct, AddonSourceRanker.rank(listOf(torrent, direct)).first())
    }

    @Test fun higherQualityDirectSourceWins() {
        val low = source("720p direct")
        val high = source("1080p direct")
        assertEquals(high, AddonSourceRanker.rank(listOf(low, high)).first())
    }

    @Test fun equalScoresPreserveInputOrder() {
        val first = source("Direct source")
        val second = source("Another direct source")
        assertEquals(first, AddonSourceRanker.rank(listOf(first, second)).first())
    }
}
