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
    val segmentWords: List<List<OcrWord>>,
) {
    val segments: List<String> get() = segmentWords.map { segment -> segment.joinToString(" ") { it.text } }
    val box: BoundingBox get() = words.map { it.box }.reduce(BoundingBox::union)
    val text: String get() = segments.joinToString("  ")
}

/**
 * Rebuilds receipt lines from OCR words.
 *
 * Two strategies:
 * - engine lines: when the engine's text lines span the whole width (single column
 *   or single block layout), they are used as rows. The engine follows each line
 *   even when the paper is slightly skewed or curved, which pure geometry does not:
 *   a residual 1-1.5 deg tilt was enough to pair the prices with the wrong names.
 * - geometry: otherwise (automatic layout splits lines by column, older fixtures
 *   have no line info), rows are rebuilt from the word boxes. This assumes the
 *   image is roughly straight (the preprocessor deskews it).
 */
object ReceiptRowBuilder {

    /**
     * @param useEngineLines group by [OcrWord.line] when every word has one.
     * @param segmentGapFactor a horizontal gap wider than this many times the row
     * height starts a new segment.
     */
    fun build(
        words: List<OcrWord>,
        useEngineLines: Boolean = false,
        segmentGapFactor: Double = 1.5,
    ): List<ReceiptRow> {
        val valid = words.filter { it.text.isNotBlank() && it.box.height > 0 && it.box.width > 0 }
        if (valid.isEmpty()) return emptyList()

        if (useEngineLines && valid.all { it.line != null }) {
            return valid
                .groupBy { it.line!! }
                .toSortedMap()
                .values
                .map { toRow(it, segmentGapFactor) }
        }

        // Words of abnormal height must not drive row grouping:
        // - specks of noise (stray dots, dashes) a few pixels tall shrink the
        //   tolerance and split a real line in two;
        // - "words" spanning several lines (a column of single letters read top to
        //   bottom, e.g. the VAT codes) would glue neighbouring lines together.
        // Rows are built from normal-height words first; the rest is attached after.
        val medianHeight = valid.map { it.box.height.toDouble() }.median()
        val normalHeights = SMALL_WORD_RATIO * medianHeight..TALL_WORD_RATIO * medianHeight
        val (regular, irregular) = valid.partition { it.box.height.toDouble() in normalHeights }

        val rows = groupIntoRows(regular)
        val orphans = mutableListOf<OcrWord>()
        for (word in irregular) {
            val row = rows
                .filter { row -> row.any { word.box.centerY in it.box.top.toDouble()..it.box.bottom.toDouble() } }
                .minByOrNull { row -> abs(row.map { it.box.centerY }.average() - word.box.centerY) }
            if (row != null) row += word else orphans += word
        }
        // Fragments far from any line are kept as their own rows; the parser decides.
        rows += groupIntoRows(orphans)

        return rows
            .sortedBy { row -> row.map { it.box.centerY }.average() }
            .map { toRow(it, segmentGapFactor) }
    }

    private fun groupIntoRows(words: List<OcrWord>): MutableList<MutableList<OcrWord>> {
        val rows = mutableListOf<MutableList<OcrWord>>()
        var current = mutableListOf<OcrWord>()
        for (word in words.sortedBy { it.box.centerY }) {
            if (current.isEmpty() || belongsToRow(word, current)) {
                current += word
            } else {
                rows += current
                current = mutableListOf(word)
            }
        }
        if (current.isNotEmpty()) rows += current
        return rows
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
            segmentWords = segments,
        )
    }

    /** Words shorter than this fraction of the median word height count as fragments. */
    private const val SMALL_WORD_RATIO = 0.5

    /**
     * Words taller than this multiple of the median word height (multi-line junk, or
     * large headings such as the logo or the total) are attached instead of grouped.
     */
    private const val TALL_WORD_RATIO = 2.0

    private fun List<Double>.median(): Double {
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
