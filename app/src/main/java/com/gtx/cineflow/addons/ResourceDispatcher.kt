package com.gtx.cineflow.addons

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.supervisorScope

/** Catalog-only dispatcher for P1. Detail, stream and subtitle dispatch intentionally belong to later gates. */
class ResourceDispatcher(private val client: AddonClient, private val gson: Gson = Gson()) {
    fun homeRows(addons: List<AddonRecord>, perAddonCatalogLimit: Int = 8): Flow<CatalogResult> = channelFlow {
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        supervisorScope {
            addons.filter { it.enabled }.forEach { addon ->
                addon.manifest.catalogs.take(perAddonCatalogLimit.coerceIn(1, 20)).forEach { catalog ->
                    launchCatalog(addon, catalog, emptyMap(), semaphore) { send(it) }
                }
            }
        }
    }

    suspend fun search(addons: List<AddonRecord>, query: String): List<CatalogResult> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        return supervisorScope {
            addons.filter { it.enabled }.flatMap { addon ->
                addon.manifest.catalogs.filter { it.supportsSearch }.map { catalog ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit { fetchCatalog(CatalogRequest(addon, catalog, mapOf("search" to term))) }
                    }
                }
            }.awaitAll()
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.launchCatalog(
        addon: AddonRecord,
        catalog: CatalogRef,
        extra: Map<String, String>,
        semaphore: Semaphore,
        emit: suspend (CatalogResult) -> Unit
    ) = launch {
        val result = semaphore.withPermit { fetchCatalog(CatalogRequest(addon, catalog, extra)) }
        emit(result)
    }

    private suspend fun fetchCatalog(request: CatalogRequest): CatalogResult {
        val addon = request.addon
        val query = linkedMapOf<String, String>().apply {
            request.extra.forEach { (k, v) -> put(k, v) }
            request.skip?.let { put("skip", it.toString()) }
        }
        return when (val response = client.fetchJson(addon.baseUrl, listOf("catalog", request.catalog.type, "${request.catalog.id}.json"), query)) {
            is AddonResult.Failure -> CatalogResult(request, emptyList(), response.error)
            is AddonResult.Success -> {
                val metas = response.value.get("metas")
                val items = parseMetas(metas, addon.baseUrl)
                CatalogResult(request, items)
            }
        }
    }

    private fun parseMetas(element: JsonElement?, addonBaseUrl: String): List<CatalogItem> {
        if (element == null || !element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { item -> runCatching {
            if (!item.isJsonObject) return@runCatching null
            val obj = item.asJsonObject
            val id = obj.string("id") ?: return@runCatching null
            val type = obj.string("type") ?: return@runCatching null
            val name = obj.string("name") ?: return@runCatching null
            CatalogItem(
                id = id, type = type, name = name,
                poster = obj.string("poster"), posterShape = obj.string("posterShape"),
                background = obj.string("background"), logo = obj.string("logo"),
                description = obj.string("description"), releaseInfo = obj.string("releaseInfo"),
                imdbRating = obj.string("imdbRating"),
                genres = obj.arrayStrings("genres"), addonBaseUrl = addonBaseUrl
            )
        }.getOrNull() }
    }

    private fun JsonObject.string(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.arrayStrings(key: String): List<String> {
        val value = get(key) ?: return emptyList()
        if (!value.isJsonArray) return emptyList()
        return value.asJsonArray.mapNotNull { it.takeIf { v -> v.isJsonPrimitive && v.asJsonPrimitive.isString }?.asString }
    }

    companion object { const val MAX_CONCURRENT_REQUESTS = 6 }
}
