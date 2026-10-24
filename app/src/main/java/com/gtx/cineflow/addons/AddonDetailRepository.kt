package com.gtx.cineflow.addons

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Resolves Stremio-style metadata from installed addons; it never creates a stream URL. */
class AddonDetailRepository(
    context: Context,
    private val client: AddonClient = AddonClient(),
    private val store: AddonStore = AddonStore(context),
    private val manager: AddonManager = AddonManager(client),
) {
    private var restored = false

    private suspend fun enabledAddons(): List<AddonRecord> {
        if (!restored) {
            manager.restore(store.addons.first())
            restored = true
        }
        return manager.enabled()
    }

    suspend fun load(item: CatalogItem): AddonResult<AddonMetaDetail> {
        val candidates = enabledAddons().filter {
            manager.supportsResource(it, "meta", item.type, item.id)
        }.sortedByDescending { it.baseUrl == item.addonBaseUrl }

        if (candidates.isEmpty()) {
            return AddonResult.Failure(AddonError(item.addonBaseUrl, "No enabled addon declares metadata support for this title."))
        }

        var lastError: AddonError? = null
        for (addon in candidates) {
            val response = client.fetchJson(addon.baseUrl, listOf("meta", item.type, "${item.id}.json"))
            when (response) {
                is AddonResult.Failure -> lastError = response.error
                is AddonResult.Success -> {
                    val meta = response.value.get("meta")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?: response.value.takeIf { it.has("id") && it.has("name") }
                    if (meta == null) {
                        lastError = AddonError(addon.baseUrl, "Addon returned no metadata object.")
                        continue
                    }
                    val parsed = parseMeta(meta, addon)
                    if (parsed != null) return AddonResult.Success(parsed)
                    lastError = AddonError(addon.baseUrl, "Addon metadata is missing a valid id, type, or name.")
                }
            }
        }
        return AddonResult.Failure(lastError ?: AddonError(item.addonBaseUrl, "No metadata response was available."))
    }

    private fun parseMeta(obj: JsonObject, addon: AddonRecord): AddonMetaDetail? {
        val id = obj.string("id") ?: return null
        val type = obj.string("type") ?: return null
        val name = obj.string("name") ?: return null
        val item = CatalogItem(
            id = id,
            type = type,
            name = name,
            poster = obj.string("poster"),
            posterShape = obj.string("posterShape"),
            background = obj.string("background"),
            logo = obj.string("logo"),
            description = obj.string("description"),
            releaseInfo = obj.string("releaseInfo") ?: obj.string("released")?.take(4),
            imdbRating = obj.string("imdbRating"),
            genres = obj.stringList("genres"),
            addonBaseUrl = addon.baseUrl,
        )
        val videos = obj.get("videos").asArray().mapNotNull { video ->
            if (!video.isJsonObject) return@mapNotNull null
            val v = video.asJsonObject
            val videoId = v.string("id") ?: return@mapNotNull null
            val videoTitle = v.string("title") ?: v.string("name") ?: videoId
            AddonVideo(
                id = videoId,
                title = videoTitle,
                released = v.string("released"),
                thumbnail = v.string("thumbnail"),
                season = v.int("season"),
                episode = v.int("episode"),
                overview = v.string("overview"),
            )
        }
        return AddonMetaDetail(
            item = item,
            runtime = obj.string("runtime"),
            released = obj.string("released"),
            director = obj.stringList("director"),
            cast = obj.stringList("cast"),
            writers = obj.stringList("writer"),
            country = obj.stringList("country"),
            awards = obj.string("awards"),
            videos = videos,
            providerName = addon.manifest.name,
        )
    }

    private fun JsonObject.string(key: String): String? = get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.int(key: String): Int? = runCatching {
        get(key)?.takeIf { it.isJsonPrimitive }?.asInt
    }.getOrNull()

    private fun JsonObject.stringList(key: String): List<String> {
        val value = get(key) ?: return emptyList()
        if (value.isJsonArray) return value.asJsonArray.mapNotNull {
            it.takeIf { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }
                ?.asString?.trim()?.takeIf(String::isNotEmpty)
        }
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            return value.asString.split(",").map(String::trim).filter(String::isNotEmpty)
        }
        return emptyList()
    }

    private fun JsonElement?.asArray(): JsonArray =
        this?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
}
