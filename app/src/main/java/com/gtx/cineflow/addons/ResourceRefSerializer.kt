package com.gtx.cineflow.addons

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Normalizes the protocol's resource string-or-object union without allowing malformed JSON to crash callers. */
object ResourceRefSerializer {
    fun parse(json: JsonElement?): List<ResourceRef> {
        if (json == null || !json.isJsonArray) return emptyList()
        return json.asJsonArray.mapNotNull { parseOne(it) }
    }

    fun parseJson(raw: String): List<ResourceRef> = runCatching { parse(JsonParser.parseString(raw)) }.getOrDefault(emptyList())

    private fun parseOne(element: JsonElement?): ResourceRef? = runCatching {
        when {
            element == null || element.isJsonNull -> null
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                val name = element.asString.trim()
                name.takeIf { it.isNotEmpty() }?.let { ResourceRef(name = it) }
            }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val name = obj.string("name") ?: return@runCatching null
                ResourceRef(
                    name = name,
                    types = obj.stringList("types"),
                    idPrefixes = obj.stringList("idPrefixes")
                )
            }
            else -> null
        }
    }.getOrNull()

    private fun JsonObject.string(key: String): String? = get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.stringList(key: String): List<String> {
        val value = get(key) ?: return emptyList()
        if (!value.isJsonArray) return emptyList()
        return value.asJsonArray.mapNotNull {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString.trim().takeIf(String::isNotEmpty) else null
        }
    }
}
