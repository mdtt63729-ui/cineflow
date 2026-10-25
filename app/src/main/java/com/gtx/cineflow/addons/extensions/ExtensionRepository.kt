package com.gtx.cineflow.addons.extensions

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Loads CloudStream-style extension repositories for real: repo.json -> pluginLists -> plugins.
 *
 * Nothing is hardcoded except the one default repository. "Install" downloads the extension file
 * to app storage and remembers it; it does not execute the plugin, because CloudStream plugins
 * are built against CloudStream's own runtime.
 */
class ExtensionRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("cineflow_extensions", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _repos = MutableStateFlow(loadRepos())
    val repos: StateFlow<List<String>> = _repos.asStateFlow()

    private val _plugins = MutableStateFlow<List<ExtensionPlugin>>(emptyList())
    val plugins: StateFlow<List<ExtensionPlugin>> = _plugins.asStateFlow()

    private val _installed = MutableStateFlow(loadInstalled())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _busyUrl = MutableStateFlow<String?>(null)
    val busyUrl: StateFlow<String?> = _busyUrl.asStateFlow()

    private fun loadRepos(): List<String> = runCatching {
        gson.fromJson<List<String>>(prefs.getString("repos", null), object : TypeToken<List<String>>() {}.type)
            ?: emptyList()
    }.getOrDefault(emptyList()).ifEmpty { listOf(DEFAULT_REPO) }

    private fun loadInstalled(): Set<String> = runCatching {
        gson.fromJson<Set<String>>(prefs.getString("installed", null), object : TypeToken<Set<String>>() {}.type)
            ?: emptySet()
    }.getOrDefault(emptySet())

    private fun saveRepos() {
        prefs.edit().putString("repos", gson.toJson(_repos.value)).apply()
    }

    private fun saveInstalled() {
        prefs.edit().putString("installed", gson.toJson(_installed.value.toList())).apply()
    }

    fun addRepo(raw: String) {
        val url = normalizeRepoUrl(raw)
        if (url == null) {
            _status.value = "That does not look like a repository URL."
            return
        }
        if (_repos.value.contains(url)) {
            _status.value = "Repository already added."
            return
        }
        _repos.value = _repos.value + url
        saveRepos()
        _status.value = "Repository added."
    }

    fun removeRepo(url: String) {
        _repos.value = _repos.value - url
        saveRepos()
        _plugins.value = _plugins.value.filter { it.repositoryUrl != url }
        _status.value = "Repository removed."
    }

    suspend fun refresh() {
        if (_repos.value.isEmpty()) {
            _plugins.value = emptyList()
            _status.value = "No repositories added."
            return
        }
        _isLoading.value = true
        _status.value = null
        val collected = mutableListOf<ExtensionPlugin>()
        var failures = 0
        for (repo in _repos.value) {
            val indexBody = fetch(repo)
            if (indexBody == null) {
                failures++
                continue
            }
            val index = runCatching { gson.fromJson(indexBody, ExtensionRepoIndex::class.java) }.getOrNull()
            val lists = index?.pluginLists.orEmpty()
            if (lists.isEmpty()) {
                failures++
                continue
            }
            for (listUrl in lists) {
                val listBody = fetch(listUrl) ?: run {
                    failures++
                    null
                } ?: continue
                val items = runCatching {
                    gson.fromJson(listBody, Array<ExtensionPlugin>::class.java)?.toList().orEmpty()
                }.getOrDefault(emptyList())
                collected += items.map { plugin ->
                    if (plugin.repositoryUrl.isNullOrBlank()) plugin.copy(repositoryUrl = repo) else plugin
                }
            }
        }
        _plugins.value = collected
            .filter { !it.name.isNullOrBlank() && !it.url.isNullOrBlank() }
            .distinctBy { it.url }
            .sortedBy { it.displayName.lowercase() }
        _status.value = when {
            _plugins.value.isEmpty() -> "No extensions found. Check the repository URL or your network."
            failures > 0 -> "Loaded ${_plugins.value.size} extensions. $failures source(s) could not be reached."
            else -> null
        }
        _isLoading.value = false
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
                .execute()
                .use { response -> if (response.isSuccessful) response.body?.string() else null }
        }.getOrNull()
    }

    suspend fun install(plugin: ExtensionPlugin): Boolean {
        val url = plugin.url ?: return false
        _busyUrl.value = url
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "extensions").apply { mkdirs() }
                val target = File(dir, fileFor(plugin))
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                response.use {
                    if (!it.isSuccessful) {
                        false
                    } else {
                        val body = it.body
                        if (body == null) {
                            false
                        } else {
                            body.byteStream().use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            }
                            target.length() > 0L
                        }
                    }
                }
            }.getOrDefault(false)
        }
        if (ok) {
            _installed.value = _installed.value + url
            saveInstalled()
        }
        _busyUrl.value = null
        _status.value = if (ok) "Downloaded ${plugin.displayName}." else "Could not download ${plugin.displayName}."
        return ok
    }

    suspend fun uninstall(plugin: ExtensionPlugin) {
        val url = plugin.url ?: return
        _busyUrl.value = url
        withContext(Dispatchers.IO) {
            runCatching { File(File(context.filesDir, "extensions"), fileFor(plugin)).delete() }
            _installed.value = _installed.value - url
            saveInstalled()
        }
        _busyUrl.value = null
        _status.value = "Removed ${plugin.displayName}."
    }

    private fun fileFor(plugin: ExtensionPlugin): String {
        val base = plugin.internalName?.takeIf(String::isNotBlank) ?: (plugin.url ?: "").hashCode().toString()
        val safe = base.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val ext = (plugin.url ?: "").substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 5 } ?: "cs3"
        return "$safe.$ext"
    }

    companion object {
        /** CloudStream deep link form; the https form is used when fetching. */
        const val DEFAULT_REPO =
            "cloudstreamrepo://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json"

        fun normalizeRepoUrl(raw: String): String? {
            var value = raw.trim()
            if (value.isEmpty()) return null
            if (value.startsWith("cloudstreamrepo://")) {
                value = "https://" + value.removePrefix("cloudstreamrepo://")
            }
            if (!value.startsWith("https://") && !value.startsWith("http://")) return null
            return value.trimEnd('/')
        }
    }
}
