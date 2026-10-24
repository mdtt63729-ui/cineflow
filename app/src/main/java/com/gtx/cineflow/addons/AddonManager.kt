package com.gtx.cineflow.addons

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory policy layer. Network calls are delegated to AddonClient and never escape as uncaught failures. */
class AddonManager(private val client: AddonClient) {
    private val mutex = Mutex()
    private val records = linkedMapOf<String, AddonRecord>()

    suspend fun install(url: String): AddonResult<AddonRecord> {
        return when (val result = client.fetchManifest(url)) {
            is AddonResult.Failure -> result
            is AddonResult.Success -> {
                val normalized = AddonClient.normalizeBaseUrl(url)?.toString()?.trimEnd('/')
                    ?: return AddonResult.Failure(AddonError(url, "Unsafe or invalid addon URL."))
                val record = AddonRecord(normalized, result.value)
                mutex.withLock { records[normalized] = record }
                AddonResult.Success(record)
            }
        }
    }

    suspend fun restore(saved: List<AddonRecord>) = mutex.withLock {
        records.clear()
        saved.forEach { record ->
            val normalized = AddonClient.normalizeBaseUrl(record.baseUrl)?.toString()?.trimEnd('/') ?: return@forEach
            if (record.manifest.id.isNotBlank() && record.manifest.name.isNotBlank()) {
                records[normalized] = record.copy(baseUrl = normalized)
            }
        }
    }

    suspend fun all(): List<AddonRecord> = mutex.withLock { records.values.toList() }
    suspend fun enabled(): List<AddonRecord> = mutex.withLock { records.values.filter(AddonRecord::enabled) }

    suspend fun setEnabled(baseUrl: String, enabled: Boolean): Boolean = mutex.withLock {
        val key = AddonClient.normalizeBaseUrl(baseUrl)?.toString()?.trimEnd('/') ?: return@withLock false
        val old = records[key] ?: return@withLock false
        records[key] = old.copy(enabled = enabled)
        true
    }

    suspend fun uninstall(baseUrl: String): Boolean = mutex.withLock {
        val key = AddonClient.normalizeBaseUrl(baseUrl)?.toString()?.trimEnd('/') ?: return@withLock false
        records.remove(key) != null
    }

    /** Empty prefix lists mean unrestricted, per the addon manifest convention. */
    fun supportsResource(addon: AddonRecord, resource: String, type: String, id: String? = null): Boolean {
        val resourceRefs = addon.manifest.resources.map { it.name }
        val resourceSupported = resourceRefs.isEmpty() || resourceRefs.any { it.equals(resource, true) }
        val resourceTypeRefs = addon.manifest.resources.filter { it.name.equals(resource, true) }
        val typeSupported = resourceTypeRefs.isEmpty() || resourceTypeRefs.any { it.types.isEmpty() || type in it.types }
        val manifestPrefixSupported = addon.manifest.idPrefixes.isEmpty() || id == null || addon.manifest.idPrefixes.any(id::startsWith)
        val resourcePrefixSupported = resourceTypeRefs.isEmpty() || resourceTypeRefs.any {
            it.idPrefixes.isEmpty() || id == null || it.idPrefixes.any(id::startsWith)
        }
        return resourceSupported && typeSupported && manifestPrefixSupported && resourcePrefixSupported
    }
}
