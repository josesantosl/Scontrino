package com.pepesantos.scontrino.ocr.draft

import com.pepesantos.scontrino.ocr.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptLineCropsTest {

    /** Rows 20 px tall every 30 px, text from x = 100 to 900 (one row narrower). */
    private val rows = (0 until 10).map { i ->
        val right = if (i == 2) 400 else 900
        BoundingBox(left = 100, top = 30 * i + 10, right = right, bottom = 30 * i + 30)
    }

    @Test
    fun `consecutive rows split into runs`() {
        assertEquals(listOf(listOf(3, 4), listOf(9)), ReceiptLineCrops.runs(listOf(3, 4, 9)))
        assertEquals(listOf(listOf(1)), ReceiptLineCrops.runs(listOf(1)))
        assertTrue(ReceiptLineCrops.runs(emptyList()).isEmpty())
    }

    @Test
    fun `one crop per run, the width of the receipt text, with a margin`() {
        val crops = ReceiptLineCrops.crops(listOf(2, 3, 7), rows, imageWidth = 1000, imageHeight = 300)

        // Margin: 0.35 × 20 = 7 px. Same width for every crop, even the narrow row's.
        assertEquals(
            listOf(
                BoundingBox(left = 93, top = 63, right = 907, bottom = 127),
                BoundingBox(left = 93, top = 213, right = 907, bottom = 247),
            ),
            crops,
        )
    }

    @Test
    fun `crops stay inside the image and ignore unknown rows`() {
        val crops = ReceiptLineCrops.crops(listOf(0, 9, 42), rows, imageWidth = 905, imageHeight = 290)

        assertEquals(
            listOf(
                BoundingBox(left = 93, top = 3, right = 905, bottom = 37),
                BoundingBox(left = 93, top = 273, right = 905, bottom = 290),
            ),
            crops,
        )
        assertTrue(ReceiptLineCrops.crops(listOf(42), rows, 1000, 300).isEmpty())
    }
}
