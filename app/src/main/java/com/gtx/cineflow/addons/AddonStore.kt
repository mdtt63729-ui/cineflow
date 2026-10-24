package com.gtx.cineflow.addons

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.cineFlowAddonDataStore by preferencesDataStore(name = "cineflow_addons_v1")

/** Versioned JSON persistence; corrupt storage degrades to an empty list and is repaired on next save. */
class AddonStore(private val context: Context, private val gson: Gson = AddonJson.gson) {
    private val key = stringPreferencesKey("installed_addons_json_v1")

    val addons: Flow<List<AddonRecord>> = context.cineFlowAddonDataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { prefs -> decode(prefs[key].orEmpty()) }

    suspend fun save(addons: List<AddonRecord>) {
        val safe = addons.filter { record ->
            AddonClient.normalizeBaseUrl(record.baseUrl) != null && record.manifest.id.isNotBlank() && record.manifest.name.isNotBlank()
        }
        context.cineFlowAddonDataStore.edit { prefs -> prefs[key] = gson.toJson(safe) }
    }

    private fun decode(raw: String): List<AddonRecord> = runCatching {
        if (raw.isBlank()) return emptyList()
        val root = JsonParser.parseString(raw)
        if (!root.isJsonArray) return emptyList()
        root.asJsonArray.mapNotNull { element ->
            runCatching { gson.fromJson(element, AddonRecord::class.java) }.getOrNull()?.let { record ->
                val normalized = AddonClient.normalizeBaseUrl(record.baseUrl)?.toString()?.trimEnd('/')
                if (normalized != null && record.manifest.id.isNotBlank() && record.manifest.name.isNotBlank()) record.copy(baseUrl = normalized) else null
            }
        }
    }.getOrDefault(emptyList())
}
