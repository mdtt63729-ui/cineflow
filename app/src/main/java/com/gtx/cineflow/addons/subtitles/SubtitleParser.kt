package com.gtx.cineflow.addons.subtitles

/** Defensive SRT/WebVTT parser with limits on input size, cue count and cue text. */
object SubtitleParser {
    private val timing = Regex("""^\s*(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}|\d{1,2}:\d{2}[,.]\d{1,3})\s*-->\s*(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}|\d{1,2}:\d{2}[,.]\d{1,3})(?:\s+.*)?$""")

    fun parse(raw: String, maxChars: Int = 2_000_000, maxCues: Int = 20_000): List<SubtitleCue> {
        require(raw.length <= maxChars) { "Subtitle file is too large." }
        val lines = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').lines()
        val cues = ArrayList<SubtitleCue>()
        var i = 0
        while (i < lines.size && cues.size < maxCues) {
            val match = timing.matchEntire(lines[i].trim())
            if (match == null) { i++; continue }
            val start = parseTime(match.groupValues[1])
            val end = parseTime(match.groupValues[2])
            i++
            val text = ArrayList<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (lines[i].length <= 4000) text += lines[i].trim()
                i++
            }
            if (start != null && end != null && end >= start && text.isNotEmpty()) {
                val cleaned = text.joinToString("\n")
                    .replace(Regex("""</?(?:i|b|u|font|c(?:\.[^ >]+)?|v(?:\s+[^>]*)?)\b[^>]*>""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\{\\an\d+\}"""), "").take(8000)
                if (cleaned.isNotBlank()) cues += SubtitleCue(start, end, cleaned)
            }
        }
        return cues
    }

    private fun parseTime(value: String): Long? {
        return try {
            val parts = value.replace(',', '.').split(':')
            val secondsPart = parts.last()
            val seconds = secondsPart.substringBefore('.').toLong()
            val fraction = (secondsPart.substringAfter('.', "0") + "000").take(3).toLong()
            val wholeParts = parts.dropLast(1) + secondsPart.substringBefore('.')
            if (wholeParts.any { it.toLongOrNull() == null }) return null
            val totalSeconds = when (parts.size) {
                2 -> parts[0].toLong() * 60 + seconds
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + seconds
                else -> return null
            }
            totalSeconds * 1000 + fraction
        } catch (_: Exception) {
            null
        }
    }
}
