package com.pepesantos.scontrino.ocr.draft

import com.pepesantos.scontrino.ocr.BoundingBox
import kotlin.math.roundToInt

/**
 * Where to crop the receipt image to show the lines an item was read from, above
 * its card in the review screen.
 */
object ReceiptLineCrops {

    /** Vertical margin around the lines, as a fraction of the median line height. */
    private const val MARGIN_RATIO = 0.35

    /**
     * One crop per run of consecutive rows in [rows] (an item bought twice at different
     * points of the receipt gets two). Every crop spans the width of the receipt's text,
     * not just its own line, so all lines show at the same scale and stay aligned.
     *
     * @param rowBoxes the box of each row of the reading, in image coordinates.
     */
    fun crops(rows: List<Int>, rowBoxes: List<BoundingBox>, imageWidth: Int, imageHeight: Int): List<BoundingBox> {
        val valid = rows.filter { it in rowBoxes.indices }.distinct().sorted()
        if (valid.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val margin = (MARGIN_RATIO * rowBoxes.map { it.height }.sorted()[rowBoxes.size / 2]).roundToInt()
        val left = (rowBoxes.minOf { it.left } - margin).coerceAtLeast(0)
        val right = (rowBoxes.maxOf { it.right } + margin).coerceAtMost(imageWidth)

        return runs(valid).map { run ->
            val box = run.map(rowBoxes::get).reduce(BoundingBox::union)
            BoundingBox(
                left = left,
                top = (box.top - margin).coerceAtLeast(0),
                right = right,
                bottom = (box.bottom + margin).coerceAtMost(imageHeight),
            )
        }.filter { it.height > 0 && it.width > 0 }
    }

    /** Sorted row indices split where they stop being consecutive: [3, 4, 9] → [[3, 4], [9]]. */
    internal fun runs(sorted: List<Int>): List<List<Int>> {
        val runs = mutableListOf<MutableList<Int>>()
        for (row in sorted) {
            if (runs.isNotEmpty() && runs.last().last() == row - 1) runs.last() += row else runs += mutableListOf(row)
        }
        return runs
    }
}
