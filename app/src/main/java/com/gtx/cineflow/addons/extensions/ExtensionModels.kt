package com.gtx.cineflow.addons.extensions

/**
 * CloudStream-compatible extension repository models.
 *
 * A repository index (`repo.json`) lists plugin list URLs; each plugin list is a JSON array of
 * plugin entries like the ones published by CloudStream repos. Everything is nullable because
 * Gson fills these reflectively and real repositories omit fields.
 */
data class ExtensionRepoIndex(
    val name: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val manifestVersion: Int? = null,
    val pluginLists: List<String>? = null,
)

data class ExtensionPlugin(
    val url: String? = null,
    val name: String? = null,
    val internalName: String? = null,
    val authors: List<String>? = null,
    val description: String? = null,
    val repositoryUrl: String? = null,
    val iconUrl: String? = null,
    val tvTypes: List<String>? = null,
    val language: String? = null,
    val status: Int? = null,
    val apiVersion: Int? = null,
    val fileSize: Long? = null,
    val fileHash: String? = null,
    val version: Int? = null,
) {
    val displayName: String get() = name?.takeIf(String::isNotBlank) ?: internalName.orEmpty()

    /** Which of the app's category tabs this extension belongs to. */
    val categories: List<String>
        get() {
            val types = (tvTypes ?: emptyList()).flatMap { it.split(',') }.map { it.trim().lowercase() }
            val result = mutableListOf<String>()
            if (types.any { it == "movie" || it == "animemovie" || it == "cartoon" || it == "ova" }) result += "Movies"
            if (types.any { it == "tvseries" || it == "live" }) result += "TV Series"
            if (types.any { it == "anime" || it == "animemovie" || it == "ova" }) result += "Anime"
            if (types.any { it == "asiandrama" }) result += "Asian Dramas"
            if (result.isEmpty()) result += "Movies"
            return result
        }

    val statusLabel: String
        get() = when (status) {
            1 -> "Working"
            2 -> "Broken"
            3 -> "Slow"
            else -> ""
        }

    val isBroken: Boolean get() = status == 2
}

object ExtensionFormat {
    fun flag(language: String?): String = when (language?.lowercase()?.substringBefore('-')) {
        "en" -> "🇬🇧"; "hi" -> "🇮🇳"; "bn" -> "🇧🇩"; "ta" -> "🇮🇳"; "te" -> "🇮🇳"
        "ml" -> "🇮🇳"; "kn" -> "🇮🇳"; "mr" -> "🇮🇳"; "pa" -> "🇮🇳"; "ur" -> "🇵🇰"
        "ar" -> "🇸🇦"; "es", "mx" -> "🇪🇸"; "fr" -> "🇫🇷"; "de" -> "🇩🇪"; "it" -> "🇮🇹"
        "pt", "pt-br" -> "🇧🇷"; "tr" -> "🇹🇷"; "id" -> "🇮🇩"; "ms" -> "🇲🇾"; "th" -> "🇹🇭"
        "vi" -> "🇻🇳"; "ja" -> "🇯🇵"; "ko" -> "🇰🇷"; "zh" -> "🇨🇳"; "ru" -> "🇷🇺"
        "pl" -> "🇵🇱"; "nl" -> "🇳🇱"; "el", "gr" -> "🇬🇷"; "ro" -> "🇷🇴"; "hu" -> "🇭🇺"
        else -> "🌐"
    }

    fun languageName(language: String?): String = when (language?.lowercase()?.substringBefore('-')) {
        "en" -> "English"; "hi" -> "Hindi"; "bn" -> "Bengali"; "ta" -> "Tamil"; "te" -> "Telugu"
        "ml" -> "Malayalam"; "kn" -> "Kannada"; "mr" -> "Marathi"; "pa" -> "Punjabi"; "ur" -> "Urdu"
        "ar" -> "Arabic"; "es", "mx" -> "Spanish"; "fr" -> "French"; "de" -> "German"; "it" -> "Italian"
        "pt", "pt-br" -> "Portuguese"; "tr" -> "Turkish"; "id" -> "Indonesian"; "ms" -> "Malay"
        "th" -> "Thai"; "vi" -> "Vietnamese"; "ja" -> "Japanese"; "ko" -> "Korean"; "zh" -> "Chinese"
        "ru" -> "Russian"; "pl" -> "Polish"; "nl" -> "Dutch"; "el", "gr" -> "Greek"; "ro" -> "Romanian"
        else -> language?.uppercase() ?: "Unknown"
    }

    fun size(bytes: Long?): String {
        val value = bytes ?: return ""
        if (value <= 0L) return ""
        val kb = value / 1024.0
        return if (kb < 1024.0) String.format(java.util.Locale.US, "%.0f KB", kb)
        else String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
    }
}
