package com.pepesantos.scontrino.ocr

import com.pepesantos.scontrino.ocr.draft.ProductNames
import com.pepesantos.scontrino.ocr.parse.ParsedItem
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import com.pepesantos.scontrino.ocr.parse.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutChoiceTest {

    private fun item(cents: Long, name: String = "ARTICOLO", nameConfidence: Float = 80f, correctedFrom: Long? = null) =
        ParsedItem(
            name = name, code = null, priceCents = cents, priceConfidence = 90f,
            nameConfidence = nameConfidence, correctedFromCents = correctedFrom, rowIndex = 0,
        )

    private fun attempt(mode: OcrPageSegMode, items: List<ParsedItem>, total: Long?, unparsed: Int = 0) =
        LayoutAttempt(
            mode = mode,
            result = OcrResult(
                imageWidth = 100, imageHeight = 100, words = emptyList(),
                rawText = "", languages = emptyList(), durationMs = 0,
            ),
            rows = emptyList(),
            parsed = ParsedReceipt(
                items = items, totalCents = total, date = null, vatNumber = null,
                headerLines = emptyList(), unparsedRows = List(unparsed) { "?" },
            ),
        )

    @Test
    fun `a reading that adds up wins over one with better names that does not`() {
        // The one with better names skipped a hard line: fewer, easier words.
        val skipsALine = attempt(OcrPageSegMode.SPARSE, listOf(item(100, nameConfidence = 95f)), total = 300)
        val complete = attempt(OcrPageSegMode.AUTO, listOf(item(100, nameConfidence = 70f), item(200)), total = 300)
        assertEquals(OcrPageSegMode.AUTO, LayoutChoice.best(listOf(skipsALine, complete)).mode)
    }

    @Test
    fun `among readings that add up, the one whose names the user already has wins`() {
        val known = setOf(ProductNames.normalize("SOYADRINK CLASSICO LT 1"))
        // Same amounts; one misread the name, with even higher confidence.
        val misread = attempt(OcrPageSegMode.AUTO, listOf(item(249, "SOVADRINK CLASSICO LT 1", 95f)), total = 249)
        val right = attempt(OcrPageSegMode.SPARSE, listOf(item(249, "SOYADRINK CLASSICO LT 1", 85f)), total = 249)
        assertEquals(OcrPageSegMode.SPARSE, LayoutChoice.best(listOf(misread, right), known).mode)
    }

    @Test
    fun `among readings that add up, more confident names win even if noisier`() {
        val confident = attempt(OcrPageSegMode.AUTO, listOf(item(100, nameConfidence = 90f)), total = 100, unparsed = 3)
        val quiet = attempt(OcrPageSegMode.SPARSE, listOf(item(100, nameConfidence = 60f)), total = 100, unparsed = 0)
        assertEquals(OcrPageSegMode.AUTO, LayoutChoice.best(listOf(quiet, confident)).mode)
    }

    @Test
    fun `among readings that add up, a guessed price loses even with better names`() {
        val corrected = attempt(OcrPageSegMode.AUTO, listOf(item(199, nameConfidence = 95f, correctedFrom = 1199)), total = 199)
        val clean = attempt(OcrPageSegMode.SPARSE, listOf(item(199, nameConfidence = 70f)), total = 199)
        assertEquals(OcrPageSegMode.SPARSE, LayoutChoice.best(listOf(corrected, clean)).mode)
    }

    @Test
    fun `when none adds up, more items wins`() {
        val few = attempt(OcrPageSegMode.SINGLE_COLUMN, listOf(item(100)), total = 999)
        val more = attempt(OcrPageSegMode.AUTO, listOf(item(100), item(200)), total = 999)
        assertEquals(OcrPageSegMode.AUTO, LayoutChoice.best(listOf(few, more)).mode)
    }

    @Test
    fun `when none adds up, fewer uninterpreted rows beat name confidence`() {
        val noisy = attempt(OcrPageSegMode.AUTO, listOf(item(100, nameConfidence = 95f)), total = 999, unparsed = 3)
        val clean = attempt(OcrPageSegMode.SPARSE, listOf(item(100, nameConfidence = 60f)), total = 999, unparsed = 0)
        assertEquals(OcrPageSegMode.SPARSE, LayoutChoice.best(listOf(noisy, clean)).mode)
    }

    @Test
    fun `on a full tie the mode tried first is kept`() {
        val first = attempt(OcrPageSegMode.SINGLE_COLUMN, listOf(item(100)), total = 100)
        val second = attempt(OcrPageSegMode.AUTO, listOf(item(100)), total = 100)
        assertEquals(OcrPageSegMode.SINGLE_COLUMN, LayoutChoice.best(listOf(first, second)).mode)
    }

    @Test
    fun `only a reading that adds up skips the other modes`() {
        assertTrue(LayoutChoice.isGoodEnough(attempt(OcrPageSegMode.SINGLE_COLUMN, listOf(item(100)), total = 100)))
        assertFalse(LayoutChoice.isGoodEnough(attempt(OcrPageSegMode.SINGLE_COLUMN, listOf(item(100)), total = 250)))
        assertFalse(LayoutChoice.isGoodEnough(attempt(OcrPageSegMode.SINGLE_COLUMN, listOf(item(100)), total = null)))
    }

    @Test
    fun `fallbacks are every other mode`() {
        val fallbacks = LayoutChoice.fallbacksAfter(OcrPageSegMode.SINGLE_COLUMN)
        assertEquals(OcrPageSegMode.entries.size - 1, fallbacks.size)
        assertFalse(OcrPageSegMode.SINGLE_COLUMN in fallbacks)
    }

    @Test
    fun `real receipt - single column adds up, and name confidence counts only the names`() {
        val words = OcrFixtures.words("bennet-torino-san-paolo-2026-11-08")
        val result = OcrResult(1854, 2541, words, rawText = "", languages = emptyList(), durationMs = 0)
        val reading = LayoutAttempt.of(OcrPageSegMode.SINGLE_COLUMN, result)
        assertTrue(LayoutChoice.isGoodEnough(reading))

        // "SAC .TRASP.BENNET 110LT P": 60.9, 60.9, 85.7 and 91.2 (code-less, all name words).
        val bag = reading.parsed.items.single { it.name.startsWith("SAC ") }
        assertEquals((60.9 + 60.9 + 85.7 + 91.2) / 4, bag.nameConfidence.toDouble(), 0.05)
        assertEquals(2, reading.knownNames(setOf("PIZZA POMODORISSIM", "VERDURE ORTO PASTELLA")))
    }

    @Test
    fun `the parser leaves the article code out of the name confidence`() {
        val rows = ReceiptRowBuilder.build(
            listOf(
                OcrWord("265", BoundingBox(0, 0, 30, 20), 10f, line = 0),
                OcrWord("PIZZA", BoundingBox(40, 0, 90, 20), 90f, line = 0),
                OcrWord("1,35", BoundingBox(300, 0, 340, 20), 90f, line = 0),
                OcrWord("TOTALE", BoundingBox(0, 30, 60, 50), 90f, line = 1),
                OcrWord("1,35", BoundingBox(300, 30, 340, 50), 90f, line = 1),
            ),
            useEngineLines = true,
        )
        assertEquals(90f, ReceiptParser.parse(rows).items.single().nameConfidence, 0.01f)
    }
}
