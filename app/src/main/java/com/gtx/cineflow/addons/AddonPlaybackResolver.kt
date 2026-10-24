package com.gtx.cineflow.addons

import android.net.Uri
import androidx.media3.common.MimeTypes

data class PlaybackItem(
    val uri: Uri,
    val title: String,
    val mimeType: String? = null,
    val addonName: String,
)

sealed interface PlaybackResolution {
    data class Playable(val item: PlaybackItem) : PlaybackResolution
    data class Rejected(val reason: String) : PlaybackResolution
}

/** Validates a direct addon source before handing it to Media3. */
object AddonPlaybackResolver {
    fun resolve(source: AddonSource): PlaybackResolution {
        if (source.isTorrent) {
            return PlaybackResolution.Rejected("Torrent playback is not available in P4. It requires the P5 torrent engine.")
        }
        val raw = source.url?.trim().orEmpty()
        if (raw.isEmpty()) return PlaybackResolution.Rejected("This source has no direct stream URL.")
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
            ?: return PlaybackResolution.Rejected("The source URL is malformed.")
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val loopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
        if (scheme != "https" && !(scheme == "http" && loopback)) {
            return PlaybackResolution.Rejected("Only HTTPS stream URLs are allowed; HTTP is allowed only for localhost.")
        }
        if (host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
            return PlaybackResolution.Rejected("The source URL contains unsupported or unsafe parts.")
        }
        if (uri.port !in -1..65535 || uri.port == 0) {
            return PlaybackResolution.Rejected("The source URL has an invalid port.")
        }
        return PlaybackResolution.Playable(
            PlaybackItem(uri, source.displayName, inferMimeType(uri.lastPathSegment.orEmpty()), source.addonName)
        )
    }

    private fun inferMimeType(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "m3u8" -> MimeTypes.APPLICATION_M3U8
        "mpd" -> MimeTypes.APPLICATION_MPD
        "mp4", "m4v" -> MimeTypes.VIDEO_MP4
        "webm" -> MimeTypes.VIDEO_WEBM
        "mkv" -> "video/x-matroska"
        "mp3" -> MimeTypes.AUDIO_MPEG
        "m4a", "aac" -> MimeTypes.AUDIO_AAC
        "ogg", "oga", "opus" -> MimeTypes.AUDIO_OGG
        else -> null
    }
}
