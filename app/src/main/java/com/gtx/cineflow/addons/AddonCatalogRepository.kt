package com.gtx.cineflow.addons

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * P2 application-facing coordinator for addon home catalogs and federated catalog search.
 *
 * Each addon request is isolated by ResourceDispatcher. A broken addon therefore contributes
 * an error row/result but does not prevent other enabled addons from returning content.
 */
class AddonCatalogRepository(
    context: Context,
    private val client: AddonClient = AddonClient(),
    private val store: AddonStore = AddonStore(context),
    private val manager: AddonManager = AddonManager(client),
    private val dispatcher: ResourceDispatcher = ResourceDispatcher(client),
) {
    private val operationMutex = Mutex()

    private val _homeRows = MutableStateFlow<List<CatalogResult>>(emptyList())
    val homeRows: StateFlow<List<CatalogResult>> = _homeRows.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    private var restored = false

    private suspend fun ensureRestored() {
        if (restored) return
        operationMutex.withLock {
            if (restored) return
            manager.restore(store.addons.first())
            restored = true
        }
    }

    suspend fun installedAddons(): List<AddonRecord> {
        ensureRestored()
        return manager.all()
    }

    /**
     * Installs missing public defaults once and persists successful manifests.
     * Existing user settings and disabled states are preserved; unavailable endpoints are skipped
     * as failures and can be retried later.
     */
    suspend fun installBuiltInsIfMissing(): List<AddonResult<AddonRecord>> {
        ensureRestored()
        val installed = manager.all().mapNotNull {
            AddonClient.normalizeBaseUrl(it.baseUrl)?.toString()?.trimEnd('/')
        }.toMutableSet()
        val results = mutableListOf<AddonResult<AddonRecord>>()
        for (spec in BuiltInAddonRegistry.addons) {
            val normalized = AddonClient.normalizeBaseUrl(spec.baseUrl)?.toString()?.trimEnd('/')
                ?: continue
            if (normalized in installed) continue
            val result = try {
                manager.install(normalized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AddonResult.Failure(AddonError(normalized, error.message ?: "Could not install built-in add-on."))
            }
            results += result
            if (result is AddonResult.Success) installed += normalized
        }
        if (results.any { it is AddonResult.Success }) persistManagerState()
        return results
    }

    suspend fun install(url: String): AddonResult<AddonRecord> {
        ensureRestored()
        return when (val result = manager.install(url)) {
            is AddonResult.Failure -> result
            is AddonResult.Success -> {
                persistManagerState()
                result
            }
        }
    }

    suspend fun setEnabled(baseUrl: String, enabled: Boolean): Boolean {
        ensureRestored()
        val changed = manager.setEnabled(baseUrl, enabled)
        if (changed) {
            persistManagerState()
            refreshHome()
        }
        return changed
    }

    suspend fun uninstall(baseUrl: String): Boolean {
        ensureRestored()
        val removed = manager.uninstall(baseUrl)
        if (removed) {
            persistManagerState()
            refreshHome()
        }
        return removed
    }

    private suspend fun persistManagerState() {
        store.save(manager.all())
    }

    /** Emits rows progressively in dispatcher completion order; the UI can update as each finishes. */
    suspend fun refreshHome() {
        ensureRestored()
        _isLoadingHome.value = true
        _homeError.value = null
        _homeRows.value = emptyList()
        try {
            val enabled = manager.enabled()
            if (enabled.isEmpty()) return
            dispatcher.homeRows(enabled).collect { result ->
                _homeRows.value = _homeRows.value + result
            }
            if (_homeRows.value.isEmpty()) {
                _homeError.value = "Installed addons returned no catalogs."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _homeError.value = "Add-on catalogs could not be loaded. Check your network or addon settings."
        } finally {
            _isLoadingHome.value = false
        }
    }

    /**
     * Searches only catalogs whose manifest explicitly advertises the `search` extra.
     * Results are de-duplicated by type + id + addon origin, preserving distinct providers.
     */
    suspend fun search(query: String): List<CatalogResult> {
        ensureRestored()
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        return try {
            dispatcher.search(manager.enabled(), term).map { result ->
                result.copy(items = result.items.distinctBy { "${it.type}:${it.id}:${it.addonBaseUrl}" })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun flatten(rows: List<CatalogResult>): List<CatalogItem> =
        rows.asSequence().filter { it.error == null }.flatMap { it.items.asSequence() }
            .distinctBy { "${it.type}:${it.id}:${it.addonBaseUrl}" }.toList()
}
