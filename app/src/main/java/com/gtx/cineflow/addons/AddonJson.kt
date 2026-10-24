package com.gtx.cineflow.addons

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/** Shared Gson configuration handles Stremio's resources union (string or object). */
object AddonJson {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(AddonManifest::class.java, ManifestDeserializer())
        .create()

    private class ManifestDeserializer : JsonDeserializer<AddonManifest> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AddonManifest {
            if (!json.isJsonObject) throw JsonParseException("Addon manifest must be an object")
            val obj = json.asJsonObject
            fun string(key: String, default: String = ""): String = obj.get(key)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: default
            fun nullableString(key: String): String? = string(key).takeIf(String::isNotBlank)
            fun strings(key: String): List<String> {
                val value = obj.get(key) ?: return emptyList()
                if (!value.isJsonArray) return emptyList()
                return value.asJsonArray.mapNotNull { e ->
                    if (e.isJsonPrimitive && e.asJsonPrimitive.isString) e.asString.trim().takeIf(String::isNotEmpty) else null
                }
            }
            val catalogs = obj.get("catalogs")?.takeIf(JsonElement::isJsonArray)?.asJsonArray?.mapNotNull { item ->
                runCatching { context.deserialize<CatalogRef>(item, CatalogRef::class.java) }.getOrNull()
                    ?.takeIf { it.type.isNotBlank() && it.id.isNotBlank() }
            } ?: emptyList()
            val behavior = obj.get("behaviorHints")?.takeIf(JsonElement::isJsonObject)
                ?.let { runCatching { context.deserialize<BehaviorHints>(it, BehaviorHints::class.java) }.getOrNull() }
            return AddonManifest(
                id = string("id"), version = string("version"), name = string("name"),
                description = nullableString("description"), logo = nullableString("logo"), background = nullableString("background"),
                types = strings("types"), catalogs = catalogs,
                resources = ResourceRefSerializer.parse(obj.get("resources")),
                idPrefixes = strings("idPrefixes"), behaviorHints = behavior
            )
        }
    }
}
