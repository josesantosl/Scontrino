package com.pepesantos.scontrino.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row reconstruction on real OCR output. */
class ReceiptRowBuilderFixtureTest {

    private val rows = ReceiptRowBuilder.build(OcrFixtures.words("lidl-torino-2026-08-17"))

    private fun assertSameRow(name: String, price: String) {
        val matching = rows.filter { name in it.text }
        assertEquals("Exactly one row should contain \"$name\": ${rows.map { it.text }}", 1, matching.size)
        val row = matching.single()
        assertTrue(
            "\"$name\" should be on the same row as \"$price\", got \"${row.text}\"",
            row.words.any { it.text == price }
        )
    }

    @Test
    fun `every item is on the same row as its price`() {
        assertSameRow("SUCCO DI ARANCIA", "3,49")
        // The OCR reads the slashed zero of 0,55 as 9; fixing that is the parser's job.
        assertSameRow("COLA", "9,55")
        assertSameRow("FALAFEL AGLIO", "1,99")
        assertSameRow("FALAFEL PICCANTE", "1,99")
        assertSameRow("CRACKERS INTEGRALI", "1,19")
        assertSameRow("PASTA ISTANTANEA", "1,99")
        assertSameRow("VASETTI SPEZIE CURRY", "2,49")
        assertSameRow("HUMMUS CURRY", "1,19")
        assertSameRow("NIPPON QUADRATINI", "1,79")
    }

    @Test
    fun `totals are on the same row as their amount`() {
        assertSameRow("SUBTOTALE", "16,67")
        assertSameRow("TOTALE COMPLESSIVO", "16,67")
        assertSameRow("Importo pagato", "16,67")
    }

    @Test
    fun `name and price end up in different segments`() {
        val row = rows.single { "NIPPON QUADRATINI" in it.text }
        assertEquals(listOf("NIPPON QUADRATINI", "10%", "1,79"), row.segments)
    }
}
