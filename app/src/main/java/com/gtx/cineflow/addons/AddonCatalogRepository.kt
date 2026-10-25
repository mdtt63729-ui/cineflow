package com.gtx.cineflow.addons

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-facing coordinator for addon home catalogs and federated catalog search.
 *
 * Nothing is pre-installed: the user adds every add-on themselves through the Add-on URL box.
 * Each addon request is isolated by ResourceDispatcher, so a broken addon contributes an error
 * row/result but does not prevent other enabled addons from returning content.
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

    /** Snapshot of installed add-ons, for the settings screen. */
    private val _addons = MutableStateFlow<List<AddonRecord>>(emptyList())
    val addons: StateFlow<List<AddonRecord>> = _addons.asStateFlow()

    /** normalized base URL -> "ok" | "failed" for the most recent install attempt. */
    private val _installResults = MutableStateFlow<Map<String, String>>(emptyMap())
    val installResults: StateFlow<Map<String, String>> = _installResults.asStateFlow()

    private val _installSummary = MutableStateFlow("No add-ons added yet.")
    val installSummary: StateFlow<String> = _installSummary.asStateFlow()

    private var restored = false

    private suspend fun ensureRestored() {
        if (restored) return
        operationMutex.withLock {
            if (restored) return
            manager.restore(store.addons.first())
            restored = true
        }
        syncSnapshot()
    }

    private suspend fun syncSnapshot() {
        val list = manager.all()
        _addons.value = list
        _installSummary.value = if (list.isEmpty()) "No add-ons added yet." else "${list.size} add-on(s) installed."
    }

    suspend fun installedAddons(): List<AddonRecord> {
        ensureRestored()
        return manager.all()
    }

    suspend fun install(url: String): AddonResult<AddonRecord> {
        ensureRestored()
        val key = AddonClient.normalizeBaseUrl(url)?.toString()?.trimEnd('/') ?: url
        return when (val result = manager.install(url)) {
            is AddonResult.Failure -> {
                _installResults.value = _installResults.value + (key to "failed")
                result
            }
            is AddonResult.Success -> {
                persistManagerState()
                syncSnapshot()
                _installResults.value = _installResults.value + (key to "ok")
                result
            }
        }
    }

    suspend fun setEnabled(baseUrl: String, enabled: Boolean): Boolean {
        ensureRestored()
        val changed = manager.setEnabled(baseUrl, enabled)
        if (changed) {
            persistManagerState()
            syncSnapshot()
            refreshHome()
        }
        return changed
    }

    suspend fun uninstall(baseUrl: String): Boolean {
        ensureRestored()
        val removed = manager.uninstall(baseUrl)
        if (removed) {
            persistManagerState()
            syncSnapshot()
            val key = AddonClient.normalizeBaseUrl(baseUrl)?.toString()?.trimEnd('/') ?: baseUrl
            _installResults.value = _installResults.value - key
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
            // Bounded so a large add-on list cannot flood the home feed with hundreds of rows.
            val enabled = manager.enabled().take(MAX_HOME_ADDONS)
            if (enabled.isEmpty()) {
                _homeError.value = "No add-ons added yet. Open Settings → Add-ons and paste a manifest URL."
                return
            }
            dispatcher.homeRows(enabled, perAddonCatalogLimit = HOME_CATALOGS_PER_ADDON).collect { result ->
                _homeRows.value = _homeRows.value + result
            }
            if (_homeRows.value.none { it.error == null && it.items.isNotEmpty() }) {
                _homeError.value = "No add-on catalogue returned titles. Check your network or the add-on in Settings → Add-ons."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _homeError.value = "Add-on catalogues could not be loaded. Check your network or add-on settings."
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

    companion object {
        /** How many enabled add-ons may contribute rows to the home feed. */
        const val MAX_HOME_ADDONS = 14

        /** How many catalogues per add-on are shown on the home feed. */
        const val HOME_CATALOGS_PER_ADDON = 2
    }
}
