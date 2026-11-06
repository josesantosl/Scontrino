package com.pepesantos.scontrino.ocr

import kotlin.math.abs
import kotlin.math.min

/**
 * A visual line of the receipt. On receipts the product name and its price are
 * usually far apart on the same line; [segments] keeps those columns separated.
 */
data class ReceiptRow(
    /** Words ordered left to right. */
    val words: List<OcrWord>,
    /** Groups of words separated by a wide horizontal gap (e.g. name | price). */
    val segments: List<String>,
) {
    val box: BoundingBox get() = words.map { it.box }.reduce(BoundingBox::union)
    val text: String get() = segments.joinToString("  ")
}

/**
 * Rebuilds receipt lines from loose OCR words using their geometry, independently
 * of how the engine grouped them into blocks.
 *
 * Assumes the image is roughly straight (the preprocessor corrects perspective).
 */
object ReceiptRowBuilder {

    /**
     * @param segmentGapFactor a horizontal gap wider than this many times the row
     * height starts a new segment.
     */
    fun build(words: List<OcrWord>, segmentGapFactor: Double = 1.5): List<ReceiptRow> {
        val valid = words.filter { it.text.isNotBlank() && it.box.height > 0 && it.box.width > 0 }
        if (valid.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<OcrWord>>()
        var current = mutableListOf<OcrWord>()
        for (word in valid.sortedBy { it.box.centerY }) {
            if (current.isEmpty() || belongsToRow(word, current)) {
                current += word
            } else {
                rows += current
                current = mutableListOf(word)
            }
        }
        rows += current

        return rows.map { toRow(it, segmentGapFactor) }
    }

    private fun belongsToRow(word: OcrWord, row: List<OcrWord>): Boolean {
        val rowCenter = row.map { it.box.centerY }.average()
        val rowHeight = row.map { it.box.height.toDouble() }.median()
        val tolerance = 0.5 * min(rowHeight, word.box.height.toDouble())
        return abs(word.box.centerY - rowCenter) <= tolerance
    }

    private fun toRow(rowWords: List<OcrWord>, segmentGapFactor: Double): ReceiptRow {
        val sorted = rowWords.sortedBy { it.box.left }
        val maxGap = segmentGapFactor * sorted.map { it.box.height.toDouble() }.median()

        val segments = mutableListOf<MutableList<OcrWord>>(mutableListOf(sorted.first()))
        for ((previous, word) in sorted.zipWithNext()) {
            if (word.box.left - previous.box.right > maxGap) {
                segments += mutableListOf(word)
            } else {
                segments.last() += word
            }
        }
        return ReceiptRow(
            words = sorted,
            segments = segments.map { segment -> segment.joinToString(" ") { it.text } },
        )
    }

    private fun List<Double>.median(): Double {
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
