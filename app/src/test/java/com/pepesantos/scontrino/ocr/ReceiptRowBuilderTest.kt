package com.pepesantos.scontrino.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ReceiptRowBuilderTest {

    private fun word(text: String, left: Int, top: Int, right: Int, bottom: Int = top + 20) =
        OcrWord(text, BoundingBox(left, top, right, bottom), confidence = 90f)

    @Test
    fun `empty input gives no rows`() {
        assertTrue(ReceiptRowBuilder.build(emptyList()).isEmpty())
    }

    @Test
    fun `blank and degenerate words are ignored`() {
        val rows = ReceiptRowBuilder.build(
            listOf(
                word("  ", 0, 0, 10),
                word("X", 5, 5, 5), // zero width
                word("PANE", 0, 100, 60),
            )
        )
        assertEquals(listOf("PANE"), rows.map { it.text })
    }

    @Test
    fun `name and price far apart on the same line form two segments`() {
        val rows = ReceiptRowBuilder.build(
            listOf(
                word("LECHE", 10, 100, 80),
                word("ENTERA", 90, 100, 170),
                word("1,25", 400, 100, 450),
            )
        )
        assertEquals(1, rows.size)
        assertEquals(listOf("LECHE ENTERA", "1,25"), rows.single().segments)
    }

    @Test
    fun `price slightly lower than the name still belongs to the same row`() {
        val rows = ReceiptRowBuilder.build(
            listOf(
                word("PASTA", 10, 100, 80),
                word("0,89", 400, 106, 450),
            )
        )
        assertEquals(1, rows.size)
        assertEquals(listOf("PASTA", "0,89"), rows.single().segments)
    }

    @Test
    fun `consecutive lines are kept apart`() {
        val rows = ReceiptRowBuilder.build(
            listOf(
                word("MELE", 10, 100, 70),
                word("2,10", 400, 100, 450),
                word("PERE", 10, 124, 70),
                word("1,80", 400, 124, 450),
            )
        )
        assertEquals(listOf("MELE  2,10", "PERE  1,80"), rows.map { it.text })
    }

    @Test
    fun `shuffled input is ordered top to bottom and left to right`() {
        val rows = ReceiptRowBuilder.build(
            listOf(
                word("TOTALE", 10, 300, 100),
                word("3,90", 400, 200, 450),
                word("12,40", 400, 300, 460),
                word("CAFFE", 10, 200, 90),
            )
        )
        assertEquals(listOf("CAFFE  3,90", "TOTALE  12,40"), rows.map { it.text })
    }

    @Test
    fun `row box is the union of its words`() {
        val row = ReceiptRowBuilder.build(
            listOf(word("A", 10, 100, 30), word("B", 300, 104, 340, 126))
        ).single()
        assertEquals(BoundingBox(10, 100, 340, 126), row.box)
    }

    @Test
    fun `default languages put the device language first and add English`() {
        assertEquals(
            listOf(OcrLanguage.ITALIAN, OcrLanguage.ENGLISH),
            OcrLanguage.defaultFor(Locale.ITALY)
        )
        assertEquals(listOf(OcrLanguage.ENGLISH), OcrLanguage.defaultFor(Locale.US))
        assertEquals(listOf(OcrLanguage.ENGLISH), OcrLanguage.defaultFor(Locale.JAPAN))
    }
}
