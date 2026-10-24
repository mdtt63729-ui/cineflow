package com.gtx.cineflow.addons.downloads

import android.net.Uri

enum class DownloadStatus { DOWNLOADING, COMPLETED, FAILED, CANCELLED }
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val fraction: Float?,
    val status: DownloadStatus,
    val message: String? = null,
    val savedUri: Uri? = null,
)
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String? = null,
    val userAgent: String? = null,
)
