package com.aldanmaz.drivedashboard.nav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** kotlinx.serialization JsonElement uzerinde null-guvenli okuma yardimcilari (eklenti gerektirmez). */
internal fun parseJson(text: String): JsonElement {
    val el = try { Json.parseToJsonElement(text) } catch (e: Exception) { throw NavException.Parse(e.message ?: "json") }
    // kotlinx, "<html>" gibi tirnaksiz govdeyi sessizce literal sayar; API yanitlari her zaman nesne/dizidir.
    if (el !is JsonObject && el !is JsonArray) throw NavException.Parse("beklenmeyen yanıt biçimi")
    return el
}

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray
internal fun JsonObject.o(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.a(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonObject.str(key: String): String? {
    val p = this[key] as? JsonPrimitive ?: return null
    return if (p.isString) p.content else p.content.takeIf { it != "null" }
}
internal fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonElement.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
