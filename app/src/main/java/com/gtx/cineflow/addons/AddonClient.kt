package com.gtx.cineflow.addons

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.IDN
import java.util.concurrent.TimeUnit

/** Small bounded HTTP client for addon JSON endpoints. It intentionally has no arbitrary custom headers. */
class AddonClient(
    private val gson: Gson = AddonJson.gson,
    private val client: OkHttpClient = defaultClient(),
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES
) {
    suspend fun fetchManifest(inputUrl: String): AddonResult<AddonManifest> = withContext(Dispatchers.IO) {
        val normalized = normalizeBaseUrl(inputUrl)
            ?: return@withContext AddonResult.Failure(AddonError(inputUrl, "Only valid HTTPS addon URLs are allowed (loopback HTTP is permitted for local testing)."))
        when (val response = getJson(normalized.newBuilder().addPathSegments("manifest.json").build(), inputUrl)) {
            is AddonResult.Failure -> response
            is AddonResult.Success -> runCatching {
                gson.fromJson(response.value, AddonManifest::class.java)
            }.getOrNull()?.takeIf { it.id.isNotBlank() && it.name.isNotBlank() }?.let {
                AddonResult.Success(it)
            } ?: AddonResult.Failure(AddonError(inputUrl, "Invalid addon manifest."))
        }
    }

    suspend fun fetchJson(baseUrl: String, pathSegments: List<String>, query: Map<String, String> = emptyMap()): AddonResult<JsonObject> = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(baseUrl)
            ?: return@withContext AddonResult.Failure(AddonError(baseUrl, "Unsafe or invalid addon URL."))
        val builder = base.newBuilder()
        pathSegments.forEach { segment ->
            if (segment.isBlank() || segment == "." || segment == ".." || segment.contains('/') || segment.contains('\\')) {
                return@withContext AddonResult.Failure(AddonError(baseUrl, "Invalid endpoint path segment."))
            }
            builder.addPathSegment(segment)
        }
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        when (val result = getJson(builder.build(), baseUrl)) {
            is AddonResult.Failure -> result
            is AddonResult.Success -> runCatching { JsonParser.parseString(result.value).asJsonObject }.getOrNull()
                ?.let { AddonResult.Success(it) }
                ?: AddonResult.Failure(AddonError(baseUrl, "Addon returned malformed JSON."))
        }
    }

    private fun getJson(url: HttpUrl, original: String): AddonResult<String> {
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return AddonResult.Failure(AddonError(original, "Addon HTTP ${response.code}", response.code))
                val body = response.body ?: return AddonResult.Failure(AddonError(original, "Addon returned an empty response."))
                if (body.contentLength() > maxResponseBytes) return AddonResult.Failure(AddonError(original, "Addon response exceeds the size limit."))
                val output = StringBuilder()
                body.charStream().use { reader ->
                    val buffer = CharArray(4096)
                    var count: Int
                    var total = 0L
                    while (reader.read(buffer).also { count = it } >= 0) {
                        total += String(buffer, 0, count).toByteArray(Charsets.UTF_8).size
                        if (total > maxResponseBytes) return AddonResult.Failure(AddonError(original, "Addon response exceeds the size limit."))
                        output.append(buffer, 0, count)
                    }
                }
                AddonResult.Success(output.toString())
            }
        } catch (e: IOException) {
            AddonResult.Failure(AddonError(original, e.message ?: "Network request failed."))
        } catch (e: Exception) {
            AddonResult.Failure(AddonError(original, "Addon response could not be processed."))
        }
    }

    companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 2L * 1024L * 1024L

        fun normalizeBaseUrl(input: String): HttpUrl? = runCatching {
            val trimmed = input.trim().removeSuffix("/")
            val parsed = trimmed.toHttpUrlOrNull() ?: return null
            val rawHost = parsed.host.lowercase()
            val host = if (rawHost.contains(':')) rawHost else IDN.toASCII(rawHost).lowercase()
            val loopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
            if (parsed.query != null || parsed.fragment != null) return null
            if (parsed.scheme != "https" && !(parsed.scheme == "http" && loopback)) return null
            // Do not permit non-loopback private/link-local addresses over cleartext HTTP.
            if (parsed.scheme == "http" && !loopback) return null
            parsed.newBuilder().username("").password("").build()
        }.getOrNull()

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

