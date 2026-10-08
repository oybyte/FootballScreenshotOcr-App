package com.fifa.ocr.core.contract

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractMigrationTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "Missing golden fixture: $name" }
            .readText()

    @Test
    fun migratesV6WithoutDroppingLegacyHistoryEvidenceOrRowOrder() {
        val migrated = migrateV6ToV7(fixture("manifest-v6-input.json"))
        val expected = ContractJson.json.decodeFromString<TaskManifestV7>(fixture("manifest-v7-expected.json"))

        assertEquals(expected, migrated)
        assertEquals(7, migrated.schemaVersion)
        assertEquals(SLOT_ORDER, migrated.slots.keys.toList())
        assertEquals(listOf("10-01 10:00", "09-30 08:00"), migrated.slots.getValue(SlotKind.ASIAN_HANDICAP_MACAU).result!!.timelineRows.map { it.displayedAt })
        assertEquals(listOf("0.90", "0.91"), migrated.slots.getValue(SlotKind.ASIAN_HANDICAP).result!!.conflicts.single().values)
        assertEquals(listOf("asset-ah-1", "asset-ah-2"), migrated.slots.getValue(SlotKind.ASIAN_HANDICAP).result!!.conflicts.single().sourceAssets)
        assertNotNull(migrated.outcomeHistory.single())
        assertNotNull(migrated.prematchSnapshot)
        assertEquals(4, migrated.archiveState.latestRevision)
        assertEquals("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", migrated.replacementHistory!!.previousHashes.getValue("识别结果.md"))
        migrated.slots.values.forEach {
            assertEquals(CaptureSource.LEGACY, it.captureSource)
            assertEquals(CoverageState.NOT_RECORDED, it.coverage)
        }
    }

    @Test
    fun v7ManifestRoundTripsThroughStrictJson() {
        val expected = ContractJson.json.decodeFromString<TaskManifestV7>(fixture("manifest-v7-expected.json"))
        val encoded = ContractJson.json.encodeToString(expected)
        assertEquals(expected, ContractJson.json.decodeFromString<TaskManifestV7>(encoded))
    }

    @Test
    fun promptFixtureRemovesOnlyTheCompleteOutcomeSection() {
        val input = fixture("prompt-input.md")
        val expected = fixture("prompt-expected.md")
        val actual = input.replace(Regex("\\n+## 比赛结果\\n.*?(?=\\n## |\\z)", RegexOption.DOT_MATCHES_ALL), "\n")
            .let { if (it.endsWith("\n")) it else "$it\n" }
        assertEquals(expected, actual)
        assertTrue("Text without the fixed heading must remain intact".let {
            "before outcome\nFinal score unknown\n".let { text ->
                text.replace(Regex("\\n+## 比赛结果\\n.*?(?=\\n## |\\z)", RegexOption.DOT_MATCHES_ALL), "\n") == text
            }
        })
    }

    @Test
    fun captureSessionFixtureRoundTripsAndKeepsCoverageEvidence() {
        val session = ContractJson.json.decodeFromString<CaptureSessionV1>(fixture("capture-session-v1.json"))
        assertEquals(CoverageState.COMPLETE, session.coverage)
        assertEquals(2, session.coverageEvidence.segmentCount)
        assertTrue(session.coverageEvidence.topConfirmed == true)
        assertTrue(session.coverageEvidence.bottomConfirmed == true)
        assertEquals(session, ContractJson.json.decodeFromString<CaptureSessionV1>(ContractJson.json.encodeToString(session)))
        assertEquals(DiagnosticCode.SCROLL_BLOCKED, session.diagnostics.single().code)
        assertEquals(1, session.coverageEvidence.duplicateRegions.size)
        try {
            session.copy(coverageEvidence = session.coverageEvidence.copy(topConfirmed = false))
            throw AssertionError("COMPLETE coverage cannot omit top confirmation")
        } catch (_: IllegalArgumentException) {
            // COMPLETE must have top, bottom, connection, and no-gap evidence.
        }
    }

    @Test
    fun capturedV7FixtureRetainsSessionSegmentDiagnosticsAndVersionReferences() {
        val task = ContractJson.json.decodeFromString<TaskManifestV7>(fixture("manifest-v7-captured.json"))
        val slot = task.slots.getValue(SlotKind.ASIAN_HANDICAP)
        assertEquals(CaptureSource.ACCESSIBILITY, slot.captureSource)
        assertEquals(CoverageState.COMPLETE, slot.coverage)
        assertEquals(listOf("session-synthetic-001"), slot.captureSessionIds)
        assertEquals(listOf("segment-001", "segment-002"), slot.captureSegmentIds)
        assertEquals(DiagnosticCode.SCROLL_BLOCKED, slot.diagnostics.single().code)
        assertEquals("synthetic-model-1", task.modelVersions.ocrModel)
        assertEquals("synthetic-1", task.resourceVersions.providerCatalog)
    }

    @Test
    fun missingAndExplicitNullKeepV6NullableDefaults() {
        val minimal = ContractJson.json.decodeFromString<TaskManifestV7>(fixture("manifest-minimal-missing-null.json"))
        assertNull(minimal.lastOperationAt)
        assertNull(minimal.fixture)
        assertNull(minimal.outputPath)
        assertEquals(OutcomeStatus.NOT_RECORDED, minimal.outcome.status)
        assertTrue(minimal.outcomeHistory.isEmpty())

        val explicitNull = ContractJson.json.decodeFromString<TaskManifestV7>(
            """{"schema_version":7,"task_id":"nulls","last_operation_at":null,"fixture":null,"output_path":null}""",
        )
        assertNull(explicitNull.lastOperationAt)
        assertNull(explicitNull.fixture)
        assertNull(explicitNull.outputPath)
    }

    @Test
    fun unknownFieldsAndEnumsAreRejected() {
        listOf("invalid-unknown-field.json", "invalid-unknown-enum.json").forEach { name ->
            try {
                ContractJson.json.decodeFromString<TaskManifestV7>(fixture(name))
                throw AssertionError("Expected strict decode failure for $name")
            } catch (_: SerializationException) {
                // Strict decoding is part of the shared contract.
            }
        }
    }

    @Test
    fun bboxIsBoundedAndConflictEvidencePairsSurvive() {
        val migrated = migrateV6ToV7(fixture("manifest-v6-input.json"))
        val conflict = migrated.slots.getValue(SlotKind.ASIAN_HANDICAP).result!!.conflicts.single()
        assertEquals(2, conflict.assetBboxes.size)
        assertEquals(setOf("asset-ah-1", "asset-ah-2"), conflict.assetBboxes.map { it.assetId }.toSet())

        try {
            ContractJson.json.decodeFromString<NormalizedBbox>("[0.1,0.2,1.2,0.4]")
            throw AssertionError("Expected out-of-range bbox rejection")
        } catch (_: IllegalArgumentException) {
            // Coordinates are normalized against the original image.
        }
    }
}
