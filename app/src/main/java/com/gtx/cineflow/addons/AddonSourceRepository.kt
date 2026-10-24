package com.gtx.cineflow.addons

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope

/** Reads source descriptors from declared stream resources. P3 does not launch playback. */
class AddonSourceRepository(
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

    suspend fun load(item: CatalogItem): List<AddonResult<List<AddonSource>>> {
        val candidates = enabledAddons().filter {
            manager.supportsResource(it, "stream", item.type, item.id)
        }.sortedByDescending { it.baseUrl == item.addonBaseUrl }
        if (candidates.isEmpty()) {
            return listOf(AddonResult.Failure(AddonError(item.addonBaseUrl, "No enabled addon declares stream support for this title.")))
        }

        return supervisorScope {
            candidates.map { addon ->
                async {
                    when (val response = client.fetchJson(addon.baseUrl, listOf("stream", item.type, "${item.id}.json"))) {
                        is AddonResult.Failure -> response
                        is AddonResult.Success -> {
                            val streams = response.value.get("streams")
                            if (streams == null || !streams.isJsonArray) {
                                AddonResult.Success(emptyList())
                            } else {
                                val parsed = streams.asJsonArray.mapNotNull { element ->
                                    if (!element.isJsonObject) return@mapNotNull null
                                    parseStream(element.asJsonObject, addon)
                                }
                                AddonResult.Success(parsed)
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun parseStream(obj: JsonObject, addon: AddonRecord): AddonSource? {
        val url = obj.string("url")
        val infoHash = obj.string("infoHash")
        val name = obj.string("name") ?: obj.string("title") ?: addon.manifest.name
        val title = obj.string("title")
        // A source descriptor must contain a recognizable transport field; P4 validates/resolves it.
        if (url.isNullOrBlank() && infoHash.isNullOrBlank()) return null
        val fileIndex = runCatching { obj.get("fileIdx")?.asInt }.getOrNull()
        val subtitles = obj.get("subtitles").takeIf { it?.isJsonArray == true }?.asJsonArray
            ?.mapNotNull { sub -> if (sub.isJsonObject) sub.asJsonObject.string("url") else null }
            .orEmpty()
        val hints = obj.get("behaviorHints").takeIf { it?.isJsonObject == true }?.asJsonObject
            ?.entrySet()?.associate { it.key to it.value.toString() }.orEmpty()
        return AddonSource(
            addonBaseUrl = addon.baseUrl,
            addonName = addon.manifest.name,
            type = if (!infoHash.isNullOrBlank()) "torrent" else "direct",
            name = name,
            title = title,
            url = url,
            infoHash = infoHash,
            fileIndex = fileIndex,
            subtitles = subtitles,
            behaviorHints = hints,
        )
    }

    private fun JsonObject.string(key: String): String? = get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.trim()?.takeIf(String::isNotEmpty)
}
