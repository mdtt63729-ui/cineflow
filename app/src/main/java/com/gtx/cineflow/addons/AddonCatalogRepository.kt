package com.gtx.cineflow.addons

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

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

    /** Snapshot of installed add-ons, for the settings screen. */
    private val _addons = MutableStateFlow<List<AddonRecord>>(emptyList())
    val addons: StateFlow<List<AddonRecord>> = _addons.asStateFlow()

    /** Human-readable result of the last built-in install pass. */
    private val _installSummary = MutableStateFlow("Add-ons have not been loaded yet.")
    val installSummary: StateFlow<String> = _installSummary.asStateFlow()

    private var restored = false

    private suspend fun ensureRestored() {
        if (restored) return
        operationMutex.withLock {
            if (restored) return
            manager.restore(store.addons.first())
            restored = true
        }
        _addons.value = manager.all()
    }

    suspend fun installedAddons(): List<AddonRecord> {
        ensureRestored()
        return manager.all()
    }

    /**
     * Installs missing public defaults once and persists successful manifests.
     * Existing user settings and disabled states are preserved; unavailable endpoints are skipped
     * as failures and can be retried later.
     *
     * Manifests are fetched with bounded parallelism so a long list of built-ins does not take
     * one full timeout per dead endpoint. The manager's write path is mutex-guarded, so parallel
     * installs cannot corrupt state.
     */
    suspend fun installBuiltInsIfMissing(): List<AddonResult<AddonRecord>> {
        ensureRestored()
        val installed = manager.all().mapNotNull {
            AddonClient.normalizeBaseUrl(it.baseUrl)?.toString()?.trimEnd('/')
        }.toSet()
        val pending = BuiltInAddonRegistry.addons.mapNotNull { spec ->
            val normalized = AddonClient.normalizeBaseUrl(spec.baseUrl)?.toString()?.trimEnd('/')
            if (normalized == null || normalized in installed) null else normalized
        }.distinct()
        if (pending.isEmpty()) {
            _installSummary.value = "All ${BuiltInAddonRegistry.addons.size} built-in add-ons are already installed."
            _addons.value = manager.all()
            return emptyList()
        }

        val semaphore = Semaphore(INSTALL_CONCURRENCY)
        val results = supervisorScope {
            pending.map { url ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            manager.install(url)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            AddonResult.Failure(AddonError(url, error.message ?: "Could not install built-in add-on."))
                        }
                    }
                }
            }.awaitAll()
        }
        val ok = results.count { it is AddonResult.Success }
        val bad = results.size - ok
        _installSummary.value = "Installed $ok of ${pending.size} add-ons. $bad could not be reached."
        _addons.value = manager.all()
        if (ok > 0) persistManagerState()
        return results
    }

    suspend fun install(url: String): AddonResult<AddonRecord> {
        ensureRestored()
        return when (val result = manager.install(url)) {
            is AddonResult.Failure -> result
            is AddonResult.Success -> {
                persistManagerState()
                _addons.value = manager.all()
                result
            }
        }
    }

    suspend fun setEnabled(baseUrl: String, enabled: Boolean): Boolean {
        ensureRestored()
        val changed = manager.setEnabled(baseUrl, enabled)
        if (changed) {
            persistManagerState()
            _addons.value = manager.all()
            refreshHome()
        }
        return changed
    }

    suspend fun uninstall(baseUrl: String): Boolean {
        ensureRestored()
        val removed = manager.uninstall(baseUrl)
        if (removed) {
            persistManagerState()
            _addons.value = manager.all()
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
                _homeError.value = "No enabled add-ons to read catalogues from."
                return
            }
            dispatcher.homeRows(enabled, perAddonCatalogLimit = HOME_CATALOGS_PER_ADDON).collect { result ->
                _homeRows.value = _homeRows.value + result
            }
            if (_homeRows.value.none { it.error == null && it.items.isNotEmpty() }) {
                _homeError.value = "No add-on catalogue returned any titles. Check your network or retry from Settings."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _homeError.value = "Add-on catalogues could not be loaded. Check your network or addon settings."
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
        const val MAX_HOME_ADDONS = 12

        /** How many catalogues per add-on are shown on the home feed. */
        const val HOME_CATALOGS_PER_ADDON = 2

        /** Parallel manifest fetches during first-launch installation. */
        private const val INSTALL_CONCURRENCY = 6
    }
}
