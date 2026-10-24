package com.gtx.cineflow.addons

data class AddonVideo(
    val id: String,
    val title: String,
    val released: String? = null,
    val thumbnail: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
)

data class AddonMetaDetail(
    val item: CatalogItem,
    val runtime: String? = null,
    val released: String? = null,
    val director: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val country: List<String> = emptyList(),
    val awards: String? = null,
    val videos: List<AddonVideo> = emptyList(),
    val providerName: String,
)

data class AddonSource(
    val addonBaseUrl: String,
    val addonName: String,
    val type: String,
    val name: String,
    val title: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIndex: Int? = null,
    val subtitles: List<String> = emptyList(),
    val behaviorHints: Map<String, String> = emptyMap(),
) {
    val isTorrent: Boolean get() = !infoHash.isNullOrBlank()
    val displayName: String get() = title?.takeIf(String::isNotBlank)
        ?: name.takeIf(String::isNotBlank)
        ?: addonName
}

sealed interface AddonLoadState<out T> {
    data object Idle : AddonLoadState<Nothing>
    data object Loading : AddonLoadState<Nothing>
    data class Success<T>(val value: T) : AddonLoadState<T>
    data class Failure(val message: String) : AddonLoadState<Nothing>
}
