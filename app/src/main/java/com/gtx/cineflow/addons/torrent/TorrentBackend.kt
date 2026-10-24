package com.gtx.cineflow.addons.torrent

import java.io.Closeable

/**
 * Native torrent-library seam. A backend implementation owns magnet/metadata resolution,
 * piece prioritization, and hash verification, then exposes a TorrentContentSource.
 *
 * This project bundle intentionally does not embed an unverified native libtorrent artifact:
 * implement this interface with the chosen Android ABI-compatible libtorrent4j build and
 * connect piece-ready notifications to PieceScheduler. The HTTP Range server is functional
 * once a verified backend is supplied.
 */
interface TorrentBackend : Closeable {
    suspend fun open(infoHash: String, fileIndex: Int, displayName: String): TorrentContentSource
    suspend fun pause(infoHash: String)
    suspend fun resume(infoHash: String)
    suspend fun remove(infoHash: String, deleteData: Boolean = false)
}
