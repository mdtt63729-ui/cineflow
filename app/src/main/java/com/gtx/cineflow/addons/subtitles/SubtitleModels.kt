package com.gtx.cineflow.addons.subtitles

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)
data class SubtitleTrack(
    val label: String,
    val language: String? = null,
    val url: String? = null,
    val localPath: String? = null,
)
