package com.fifa.ocr.core.contract

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.encodeToJsonElement

object ContractJson {
    val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
        encodeDefaults = true
    }
}

fun migrateV6ToV7(v6Json: String): TaskManifestV7 {
    val legacy = ContractJson.json.parseToJsonElement(v6Json).jsonObject
    val version = legacy["schema_version"]?.jsonPrimitive?.int
        ?: throw SerializationException("Missing schema_version")
    require(version == 6) { "migrateV6ToV7 requires schema_version 6, received $version" }
    require(legacy.keys.none { it in V7_TASK_FIELDS }) { "A v6 task cannot contain v7-only fields" }

    val legacySlots = legacy["slots"]?.jsonObject.orEmpty()
    val migratedSlots = legacySlots.mapValues { (_, value) ->
        val slot = value.jsonObject
        require(slot.keys.none { it in V7_SLOT_FIELDS }) { "A v6 slot cannot contain v7-only fields" }
        ContractJson.json.encodeToJsonElement(SlotRecordV7.serializer(), ContractJson.json.decodeFromJsonElement<SlotRecordV7>(slot))
    }.orEmpty()
    val normalizedSlots = migratedSlots.mapValues { (_, value) ->
        val record = ContractJson.json.decodeFromJsonElement<SlotRecordV7>(value)
        ContractJson.json.encodeToJsonElement(
            SlotRecordV7.serializer(),
            record.copy(
                captureSource = CaptureSource.LEGACY,
                coverage = CoverageState.NOT_RECORDED,
                captureSessionIds = emptyList(),
                captureSegmentIds = emptyList(),
                diagnostics = emptyList(),
            ),
        )
    }
    val migrated = buildJsonObject {
        legacy.forEach { (key, value) ->
            if (key != "schema_version" && key != "slots") put(key, value)
        }
        put("schema_version", 7)
        put("slots", JsonObject(normalizedSlots))
        if ("capture_session_ids" !in legacy) put("capture_session_ids", ContractJson.json.parseToJsonElement("[]"))
        if ("resource_versions" !in legacy) putJsonObject("resource_versions") {}
        if ("model_versions" !in legacy) putJsonObject("model_versions") {}
    }
    return ContractJson.json.decodeFromJsonElement(migrated)
}

private val V7_TASK_FIELDS = setOf("capture_session_ids", "resource_versions", "model_versions")
private val V7_SLOT_FIELDS = setOf(
    "capture_source",
    "coverage",
    "capture_session_ids",
    "capture_segment_ids",
    "diagnostics",
)
