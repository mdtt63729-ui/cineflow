package com.gtx.cineflow.addons

/**
 * Safe default add-ons: metadata, official YouTube catalogue entries, legal availability discovery,
 * and subtitles. These are public manifests, not a copy of a user's personal Stremio profile.
 */
data class BuiltInAddonSpec(
    val baseUrl: String,
    val purpose: String
)

object BuiltInAddonRegistry {
    val addons: List<BuiltInAddonSpec> = listOf(
        BuiltInAddonSpec("https://v3-cinemeta.strem.io", "Movie and series metadata"),
        BuiltInAddonSpec("https://v3-channels.strem.io", "YouTube channel catalogue"),
        BuiltInAddonSpec("https://watchhub.strem.io", "Legal availability discovery"),
        BuiltInAddonSpec("https://opensubtitles-v3.strem.io", "Subtitle search")
    )
}
