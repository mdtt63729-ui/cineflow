package com.gtx.cineflow.addons.debrid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal API client. Never logs or exposes the API token in errors. */
class RealDebridClient(private val tokenProvider: suspend () -> String?) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun getUser(): RealDebridUser = withContext(Dispatchers.IO) {
        val json = execute("GET", "$BASE/user")
        runCatching { com.google.gson.Gson().fromJson(json, RealDebridUser::class.java) }
            .getOrElse { throw RealDebridException("The service returned an invalid account response.") }
    }

    /** Unrestricts a direct link supplied by the user for content they are authorized to access. */
    suspend fun unrestrictAuthorizedDirectLink(url: String): UnrestrictedLink = withContext(Dispatchers.IO) {
        val parsed = url.trim().toHttpUrlOrNull() ?: throw RealDebridException("Enter a valid HTTPS link.")
        if (parsed.scheme != "https" || parsed.host.isBlank() || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            throw RealDebridException("Only valid HTTPS links without embedded credentials are accepted.")
        }
        val token = requireToken()
        val form = FormBody.Builder().add("link", parsed.toString()).build()
        val request = Request.Builder().url("$BASE/unrestrict/link")
            .header("Authorization", "Bearer $token")
            .post(form).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw RealDebridException(friendlyError(response.code), response.code)
            runCatching { com.google.gson.Gson().fromJson(body, UnrestrictedLink::class.java) }
                .getOrElse { throw RealDebridException("The service returned an invalid link response.") }
                .also { result ->
                    val output = result.download?.takeIf { it.isNotBlank() } ?: result.link
                    if (output.isNullOrBlank() || output.toHttpUrlOrNull()?.scheme != "https") {
                        throw RealDebridException("The service did not return a valid HTTPS download URL.")
                    }
                }
        }
    }

    private suspend fun execute(method: String, url: String): String = withContext(Dispatchers.IO) {
        val token = requireToken()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .method(method, null).build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw RealDebridException(friendlyError(response.code), response.code)
                if (body.length > MAX_RESPONSE_CHARS) throw RealDebridException("The service response was too large.")
                body
            }
        } catch (e: RealDebridException) { throw e }
        catch (_: IOException) { throw RealDebridException("Network request failed. Check your connection and try again.") }
    }

    private suspend fun requireToken(): String = tokenProvider()?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw RealDebridException("Add your API token in Real-Debrid settings first.")

    private fun friendlyError(code: Int): String = when (code) {
        401, 403 -> "Authorization failed. Check the API token and account permissions."
        429 -> "The service rate limit was reached. Wait and retry."
        in 500..599 -> "The service is temporarily unavailable. Try again later."
        else -> "The service request failed (HTTP $code)."
    }

    companion object {
        private const val BASE = "https://api.real-debrid.com/rest/1.0"
        private const val MAX_RESPONSE_CHARS = 512 * 1024
    }
}
