package com.fifa.ocr.parser

import com.fifa.ocr.core.contract.SLOT_ORDER
import com.fifa.ocr.core.contract.SlotKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleBoundaryTest {
    @Test
    fun parserConsumesCoreSlotContractInWindowsOrder() {
        assertEquals(
            listOf(
                SlotKind.ASIAN_HANDICAP,
                SlotKind.ASIAN_HANDICAP_MACAU,
                SlotKind.EUROPEAN_ODDS,
                SlotKind.EUROPEAN_ODDS_MACAU,
                SlotKind.TOTAL_GOALS,
                SlotKind.TOTAL_GOALS_MACAU,
                SlotKind.KELLY,
                SlotKind.BETFAIR,
            ),
            SLOT_ORDER,
        )
    }
}
