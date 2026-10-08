package com.fifa.ocr.core.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = NormalizedBboxSerializer::class)
data class NormalizedBbox(val coordinates: List<Double>) {
    init {
        require(coordinates.size == 4) { "Normalized bbox must contain LTRB coordinates" }
        require(coordinates.all { it in 0.0..1.0 }) { "Normalized bbox coordinates must be between 0 and 1" }
    }

    val left: Double get() = coordinates[0]
    val top: Double get() = coordinates[1]
    val right: Double get() = coordinates[2]
    val bottom: Double get() = coordinates[3]
}

object NormalizedBboxSerializer : KSerializer<NormalizedBbox> {
    private val delegate = ListSerializer(Double.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: NormalizedBbox) {
        encoder.encodeSerializableValue(delegate, value.coordinates)
    }

    override fun deserialize(decoder: Decoder): NormalizedBbox =
        NormalizedBbox(decoder.decodeSerializableValue(delegate))
}

@Serializable(with = AssetBboxSerializer::class)
data class AssetBbox(val assetId: String, val bbox: NormalizedBbox)

object AssetBboxSerializer : KSerializer<AssetBbox> {
    private val delegate = ListSerializer(JsonElement.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: AssetBbox) {
        encoder.encodeSerializableValue(
            delegate,
            listOf(JsonPrimitive(value.assetId), Json.encodeToJsonElement(NormalizedBboxSerializer, value.bbox)),
        )
    }

    override fun deserialize(decoder: Decoder): AssetBbox {
        val pair = decoder.decodeSerializableValue(delegate)
        require(pair.size == 2)
        val assetId = pair[0].jsonPrimitive.content
        val bbox = Json.decodeFromJsonElement(NormalizedBboxSerializer, pair[1])
        return AssetBbox(assetId, bbox)
    }
}
