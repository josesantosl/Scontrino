package com.pepesantos.scontrino.ocr

import com.pepesantos.scontrino.ocr.draft.ProductNames
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import com.pepesantos.scontrino.ocr.parse.ReceiptParser

/** One OCR pass with a given layout mode, already turned into rows and parsed. */
data class LayoutAttempt(
    val mode: OcrPageSegMode,
    val result: OcrResult,
    val rows: List<ReceiptRow>,
    val parsed: ParsedReceipt,
) {
    /** Prices changed by the parser so that the receipt adds up (guessed prices). */
    val corrections: Int get() = parsed.items.count { it.correctedFromCents != null }

    /** Mean OCR confidence of the item names only (no prices, header or totals). */
    val nameConfidence: Double
        get() = if (parsed.items.isEmpty()) 0.0 else parsed.items.map { it.nameConfidence.toDouble() }.average()

    /** Items whose name is exactly that of a product the user already has. */
    fun knownNames(known: Set<String>): Int =
        parsed.items.count { ProductNames.normalize(it.name) in known }

    companion object {
        fun of(mode: OcrPageSegMode, result: OcrResult): LayoutAttempt {
            val rows = ReceiptRowBuilder.build(result.words, useEngineLines = mode.fullWidthLines)
            return LayoutAttempt(mode, result, rows, ReceiptParser.parse(rows))
        }
    }
}

/**
 * Picks the best reading of a receipt among several layout modes.
 *
 * A reading whose items add up to the total always wins: the amounts are right.
 * But the names matter as much: they link each item to the user's products, aliases
 * and price history ("SOVADRINK" instead of "SOYADRINK" is a different product).
 *
 * - Among readings that add up: no auto-corrected price first (a guessed price),
 *   then more names the user already has as products, then higher confidence of
 *   the names themselves, then more items.
 * - Among readings that don't add up: more items, fewer corrections, more known
 *   names, fewer uninterpreted rows, then name confidence. Confidence comes last
 *   here because a mode that skips hard lines keeps only easy words and scores higher.
 */
object LayoutChoice {

    /** Whether this reading is good enough to skip trying other modes. */
    fun isGoodEnough(attempt: LayoutAttempt): Boolean = attempt.parsed.isConsistent

    /**
     * The best attempt; on a full tie, the first one (the preferred mode).
     *
     * @param knownNames the user's product names, normalized with [ProductNames.normalize].
     */
    fun best(attempts: List<LayoutAttempt>, knownNames: Set<String> = emptySet()): LayoutAttempt {
        val whenAddsUp = compareBy<LayoutAttempt> { it.corrections }
            .thenByDescending { it.knownNames(knownNames) }
            .thenByDescending { it.nameConfidence }
            .thenByDescending { it.parsed.items.size }

        val whenItDoesNot = compareByDescending<LayoutAttempt> { it.parsed.items.size }
            .thenBy { it.corrections }
            .thenByDescending { it.knownNames(knownNames) }
            .thenBy { it.parsed.unparsedRows.size }
            .thenByDescending { it.nameConfidence }

        val bestFirst = Comparator<LayoutAttempt> { a, b ->
            val addsUp = compareValues(b.parsed.isConsistent, a.parsed.isConsistent)
            when {
                addsUp != 0 -> addsUp
                a.parsed.isConsistent -> whenAddsUp.compare(a, b)
                else -> whenItDoesNot.compare(a, b)
            }
        }
        return attempts.sortedWith(bestFirst).first()
    }

    /** The other modes to try after [first]. */
    fun fallbacksAfter(first: OcrPageSegMode): List<OcrPageSegMode> =
        OcrPageSegMode.entries.filter { it != first }
}
