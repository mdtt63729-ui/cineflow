package com.gtx.cineflow.addons.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {
    @Test fun parsesSrtAndCommaMilliseconds() {
        val cues = SubtitleParser.parse("1\n00:00:01,250 --> 00:00:02,500\n<i>Hello</i>\n\n")
        assertEquals(1, cues.size)
        assertEquals(1250L, cues.first().startMs)
        assertEquals(2500L, cues.first().endMs)
        assertEquals("Hello", cues.first().text)
    }
    @Test fun parsesWebVttAndShortTimestamps() {
        val cues = SubtitleParser.parse("WEBVTT\n\n00:01.000 --> 00:02.250\nHi there\n")
        assertEquals(1, cues.size)
        assertEquals(1000L, cues.first().startMs)
    }
    @Test fun ignoresMalformedTimings() {
        assertTrue(SubtitleParser.parse("00:xx:01.000 --> 00:00:02.000\nNope\n").isEmpty())
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedText() { SubtitleParser.parse("x".repeat(30), maxChars = 20) }
}
