package com.gtx.cineflow.addons.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Chunked direct-download writer. Uses public Downloads on Android 10+, app-specific Downloads on API 24-28. */
class MediaDownloadRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build(),
) {
    suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val safeUrl = validateUrl(request.url)
            val name = sanitizeName(request.fileName)
            val builder = Request.Builder().url(safeUrl).get()
            request.userAgent?.takeIf { it.isNotBlank() }?.let { builder.header("User-Agent", it.take(256)) }
            var uri: Uri? = null
            var partial: File? = null
            var bytes = 0L
            try {
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code}).")
                    val body = response.body ?: throw IOException("Empty download response.")
                    val total = body.contentLength().takeIf { it >= 0 }
                    val mime = request.mimeType ?: response.header("Content-Type")?.substringBefore(';') ?: "application/octet-stream"
                    val output = if (Build.VERSION.SDK_INT >= 29) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, name)
                            put(MediaStore.Downloads.MIME_TYPE, mime)
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CineFlow")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: throw IOException("Cannot create destination in Downloads.")
                        context.contentResolver.openOutputStream(uri!!, "w")
                            ?: throw IOException("Cannot open destination.")
                    } else {
                        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "CineFlow")
                        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create app Downloads folder.")
                        val target = uniqueFile(dir, name)
                        partial = File(dir, ".${target.name}.part")
                        partial!!.outputStream().buffered()
                    }
                    output.use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                bytes += n
                                onProgress(DownloadProgress(bytes, total, total?.let { bytes.toFloat() / it.coerceAtLeast(1) }, DownloadStatus.DOWNLOADING))
                            }
                            out.flush()
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 29) {
                        context.contentResolver.update(uri!!, ContentValues().apply {
                            put(MediaStore.Downloads.IS_PENDING, 0)
                        }, null, null)
                    } else {
                        val final = File(partial!!.parentFile, name)
                        val destination = uniqueFile(final.parentFile!!, name)
                        if (!partial!!.renameTo(destination)) throw IOException("Could not finalize downloaded file.")
                        partial = null
                        uri = Uri.fromFile(destination)
                    }
                    DownloadProgress(bytes, total, 1f, DownloadStatus.COMPLETED, savedUri = uri)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                partial?.let { runCatching { it.delete() } }
                onProgress(DownloadProgress(bytes, null, null, DownloadStatus.CANCELLED, "Download cancelled"))
                throw e
            } catch (e: Exception) {
                uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                partial?.let { runCatching { it.delete() } }
                onProgress(DownloadProgress(bytes, null, null, DownloadStatus.FAILED, e.message?.take(180) ?: "Download failed"))
                throw e
            }
        }

    private fun validateUrl(raw: String): String {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: throw IOException("Invalid URL.")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        val loopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
        if (host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null ||
            (scheme != "https" && !(scheme == "http" && loopback))) {
            throw IOException("Only HTTPS URLs are allowed; HTTP is allowed only for localhost.")
        }
        return uri.toString()
    }

    private fun sanitizeName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim('.', ' ').take(140)
        return cleaned.ifBlank { "CineFlow-download.bin" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").takeIf { it.isNotBlank() && it != name }?.let { ".$it" } ?: ""
        var candidate = File(dir, name)
        var index = 1
        while (candidate.exists()) { candidate = File(dir, "$base ($index)$ext"); index++ }
        return candidate
    }
}
