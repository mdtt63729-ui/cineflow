package com.gtx.cineflow.addons.torrent

import java.io.Closeable
import java.io.IOException

/**
 * Adapter contract between a torrent library and the localhost streaming server.
 * Implementations must block until requested bytes are available, or throw on timeout/failure.
 * Only use for torrents containing content you are authorized to access.
 */
interface TorrentContentSource : Closeable {
    val contentLength: Long
    val displayName: String

    /** Reads exactly [length] bytes starting at [offset], unless EOF is reached. */
    @Throws(IOException::class)
    fun readRange(offset: Long, length: Int, timeoutMillis: Long = 60_000): ByteArray
}

data class TorrentStreamDescriptor(
    val infoHash: String,
    val fileIndex: Int,
    val displayName: String,
    val contentLength: Long,
    val mimeType: String = "video/mp4",
)
