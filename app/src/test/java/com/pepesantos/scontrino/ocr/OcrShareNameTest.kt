package com.pepesantos.scontrino.ocr

import com.pepesantos.scontrino.ocr.parse.ReceiptDate
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrShareNameTest {

    private val today = ReceiptDate(2026, 11, 28)

    @Test
    fun `store and receipt date`() {
        assertEquals(
            "bennet-2026-11-08-ocr",
            OcrShareName.of("Bennet", ReceiptDate(2026, 11, 8, hour = 18, minute = 30), today),
        )
    }

    @Test
    fun `store name is turned into a file-safe slug`() {
        assertEquals("crai-piu-2026-11-28-ocr", OcrShareName.of("  Crai Più! ", null, today))
        assertEquals("carrefour-market-2026-11-28-ocr", OcrShareName.of("Carrefour/Market", null, today))
    }

    @Test
    fun `no store uses receipt`() {
        assertEquals("receipt-2026-11-08-ocr", OcrShareName.of(null, ReceiptDate(2026, 11, 8), today))
        assertEquals("receipt-2026-11-08-ocr", OcrShareName.of("  ", ReceiptDate(2026, 11, 8), today))
        // Nothing usable left after removing symbols.
        assertEquals("receipt-2026-11-08-ocr", OcrShareName.of("★", ReceiptDate(2026, 11, 8), today))
    }

    @Test
    fun `no date uses today`() {
        assertEquals("lidl-2026-11-28-ocr", OcrShareName.of("Lidl", null, today))
    }
}
