package com.gtx.cineflow.addons

import com.google.gson.annotations.SerializedName

/** Stremio-compatible manifest. Unknown fields are intentionally ignored for forward compatibility. */
data class AddonManifest(
    val id: String,
    val version: String = "",
    val name: String,
    val description: String? = null,
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),
    val catalogs: List<CatalogRef> = emptyList(),
    val resources: List<ResourceRef> = emptyList(),
    @SerializedName("idPrefixes") val idPrefixes: List<String> = emptyList(),
    val behaviorHints: BehaviorHints? = null
)

data class BehaviorHints(
    val adult: Boolean? = null,
    val p2p: Boolean? = null,
    val configurable: Boolean? = null,
    val configurationRequired: Boolean? = null
)

data class CatalogRef(
    val type: String,
    val id: String,
    val name: String? = null,
    val extra: List<ExtraProp> = emptyList(),
    val extraSupported: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val language: String? = null
) {
    /** Search is advertised only when the manifest explicitly declares a supported search property. */
    val supportsSearch: Boolean
        get() = (extra + extraSupported.map { ExtraProp(it) }).any {
            it.name.equals("search", ignoreCase = true)
        }
}

data class ExtraProp(
    val name: String,
    val isRequired: Boolean? = null,
    val options: List<String>? = null
)

data class ResourceRef(
    val name: String,
    val types: List<String> = emptyList(),
    @SerializedName("idPrefixes") val idPrefixes: List<String> = emptyList()
)

data class AddonRecord(
    val baseUrl: String,
    val manifest: AddonManifest,
    val enabled: Boolean = true,
    val installedAtEpochMs: Long = System.currentTimeMillis()
)

data class AddonError(val addonUrl: String, val message: String, val httpCode: Int? = null)

data class CatalogRequest(
    val addon: AddonRecord,
    val catalog: CatalogRef,
    val extra: Map<String, String> = emptyMap(),
    val skip: Int? = null
)

data class CatalogItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val addonBaseUrl: String
)

data class CatalogResult(val request: CatalogRequest, val items: List<CatalogItem>, val error: AddonError? = null)

sealed interface AddonResult<out T> {
    data class Success<T>(val value: T) : AddonResult<T>
    data class Failure(val error: AddonError) : AddonResult<Nothing>
}
