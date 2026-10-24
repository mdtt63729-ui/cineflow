package com.gtx.cineflow.addons.subtitles

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class SubtitleRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
) {
    suspend fun fetch(url: String, maxBytes: Long = 2_000_000): String = withContext(Dispatchers.IO) {
        val uri = Uri.parse(url.trim())
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val loopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
        if (host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null ||
            (scheme != "https" && !(scheme == "http" && loopback))) {
            throw IOException("Only HTTPS subtitle URLs are allowed; HTTP is allowed only for localhost.")
        }
        client.newCall(Request.Builder().url(uri.toString()).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Subtitle request failed (HTTP ${response.code}).")
            val body = response.body ?: throw IOException("Empty subtitle response.")
            if (body.contentLength() > maxBytes) throw IOException("Subtitle file is too large.")
            val out = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IOException("Subtitle file is too large.")
                    out.write(buffer, 0, n)
                }
            }
            out.toByteArray().toString(Charsets.UTF_8)
        }
    }
}
