package com.pepesantos.scontrino.ocr.parse

import com.pepesantos.scontrino.ocr.OcrWord
import com.pepesantos.scontrino.ocr.ReceiptRow
import java.util.Locale
import kotlin.math.abs

/**
 * Turns OCR rows into items, discounts, total and date, using only layout rules that
 * hold across stores:
 *
 * - the item list sits between the column header ("DESCRIZIONE ... PREZZO") and the
 *   first total line ("SUBTOTALE", "TOTALE");
 * - each item line ends with its price (the rightmost amount), optionally followed
 *   by a VAT letter, and may contain a VAT rate;
 * - a negative amount, or a "Sconto" line, is a discount on the previous item;
 * - the total is cross-checked with the payment lines, and if the items do not add
 *   up, a single misread price (one digit off) is corrected.
 *
 * Store-specific formats (quantities, weights) will be added with more receipts.
 */
object ReceiptParser {

    private val COLUMN_HEADER = listOf("DESCRIZIONE", "DESCRIPCION", "DESCRIPCIÓN", "PREZZO")
    private val DISCOUNT = listOf("SCONT", "DESCUENTO", "RABATT", "REMISE")
    private val PAYMENT = listOf("PAGAMENTO", "PAGATO", "BANCOMAT", "CONTANTI", "IMPORTO")

    /** Lines with an amount that is not the total: VAT breakdown, change, savings. */
    private val NOT_TOTAL = listOf("IVA", "RESTO", "SCONT", "RISPARMI")

    private val VAT_LEGEND = Regex("""^([A-F])\s*[:.]?\s*IVA\s*(\d{1,2}(?:[,.]\d{1,2})?)\s*%""")
    private val VAT_LETTER = Regex("""^[A-F]$""")
    private val VAT_NUMBER = Regex("""P\.?\s*I\.?\s*V\.?\s*A\.?\s*:?\s*(\d{11})""")

    fun parse(rows: List<ReceiptRow>): ParsedReceipt {
        val upper = rows.map { it.text.uppercase(Locale.ROOT) }

        val columnHeader = upper.indexOfFirst { line -> COLUMN_HEADER.any { it in line } }
        val start = if (columnHeader >= 0) columnHeader + 1 else 0
        val end = (start until rows.size).firstOrNull { "TOTAL" in upper[it] } ?: rows.size

        val vatLegend = upper.mapNotNull { line ->
            VAT_LEGEND.find(line.trim())?.destructured?.let { (letter, rate) ->
                letter to rate.replace(',', '.').toDouble()
            }
        }.toMap()

        val items = mutableListOf<ParsedItem>()
        val unparsed = mutableListOf<String>()
        // A name whose price ended up on the next row (e.g. a long, wrapped name).
        var pendingName: Name? = null

        for (index in start until end) {
            val row = rows[index]
            val line = readLine(row, index, vatLegend)
            when {
                line == null -> {
                    pendingName?.let { unparsed += it.text }
                    pendingName = cleanName(row.words).takeIf { it.isValid }
                    if (pendingName == null && row.text.isNotBlank()) unparsed += row.text
                }

                line.isDiscount -> {
                    val last = items.lastIndex
                    if (last >= 0) {
                        items[last] = items[last].let { it.copy(discountCents = it.discountCents + abs(line.cents)) }
                    } else {
                        unparsed += row.text
                    }
                    pendingName = null
                }

                line.name.isValid -> {
                    pendingName?.let { unparsed += it.text }
                    pendingName = null
                    items += line.toItem(line.name)
                }

                pendingName != null && line.cents > 0 -> {
                    items += line.toItem(pendingName)
                    pendingName = null
                }

                else -> unparsed += row.text
            }
        }
        pendingName?.let { unparsed += it.text }

        val itemsTotal = items.sumOf { it.netCents }
        val total = chooseTotal(rows, upper, from = end, itemsTotal = itemsTotal)
        val checkedItems = if (total != null) correctSingleMisread(items, total) else items

        return ParsedReceipt(
            items = checkedItems,
            totalCents = total,
            date = ReceiptDateParser.find(rows.map { it.text }),
            vatNumber = upper.firstNotNullOfOrNull { VAT_NUMBER.find(it)?.groupValues?.get(1) },
            headerLines = rows.take(if (columnHeader >= 0) columnHeader else start)
                .map { it.text }
                .filter { it.isNotBlank() },
            unparsedRows = unparsed,
            footerLines = rows.drop(end).map { it.text }.filter { it.isNotBlank() },
        )
    }

    // region Lines

    private class Line(
        val cents: Long,
        val priceWord: OcrWord,
        val name: Name,
        val vatCode: String?,
        val vatRate: Double?,
        val isDiscount: Boolean,
        val rowIndex: Int,
    ) {
        fun toItem(name: Name) = ParsedItem(
            name = name.text,
            code = name.code,
            priceCents = cents,
            vatCode = vatCode,
            vatRate = vatRate,
            priceConfidence = priceWord.confidence,
            rowIndex = rowIndex,
        )
    }

    /** Reads a row that contains an amount, or returns null if it has none. */
    private fun readLine(row: ReceiptRow, index: Int, vatLegend: Map<String, Double>): Line? {
        val words = row.words
        val priceIndex = words.indices.lastOrNull { Amounts.parseCents(words[it].text) != null } ?: return null
        val priceWord = words[priceIndex]
        val cents = Amounts.parseCents(priceWord.text)!!

        val vatLetter = words.getOrNull(priceIndex + 1)?.text?.takeIf { VAT_LETTER.matches(it) }
        val percentWord = words.subList(0, priceIndex).lastOrNull { Amounts.parsePercent(it.text) != null }
        val vatRate = percentWord?.let { Amounts.parsePercent(it.text) } ?: vatLetter?.let { vatLegend[it] }

        // The name is the first column, unless the price shares it (short rows).
        val firstSegment = row.segmentWords.first()
        val nameWords = if (priceWord in firstSegment) words.subList(0, priceIndex) else firstSegment
        val name = cleanName(nameWords)

        val upperName = name.text.uppercase(Locale.ROOT)
        return Line(
            cents = cents,
            priceWord = priceWord,
            name = name,
            vatCode = vatLetter ?: percentWord?.text?.let { "${Amounts.parsePercent(it)?.toInt()}%" },
            vatRate = vatRate,
            isDiscount = cents < 0 || DISCOUNT.any { it in upperName },
            rowIndex = index,
        )
    }

    private class Name(val text: String, val code: String?) {
        /** A product name needs some letters; "." or "00" is OCR noise. */
        val isValid: Boolean get() = text.count { it.isLetter() } >= 2
    }

    private fun cleanName(words: List<OcrWord>): Name {
        val tokens = words.map { it.text }
            .filter { token -> token.any { it.isLetterOrDigit() } }
            // Receipts print names in capitals; lone lowercase letters are specks.
            .filterNot { it.length == 1 && it[0].isLowerCase() }
            .filter { Amounts.parsePercent(it) == null }
        // Some stores print an article code first: "353406 CONT.LEGGEREZZA G".
        val hasCode = tokens.size > 1 && tokens.first().length >= 3 && tokens.first().all { it.isDigit() }
        val nameTokens = if (hasCode) tokens.drop(1) else tokens
        return Name(
            text = nameTokens.joinToString(" ").trim { it in ".,;:|-_=" || it.isWhitespace() },
            code = if (hasCode) tokens.first() else null,
        )
    }

    // endregion

    // region Total

    /**
     * The printed total is cross-checked with the payment lines, since a large or
     * condensed total font is often misread ("1768", "19,9"). Preference: an amount
     * equal to the items' sum, then the most repeated one, then the total line.
     */
    private fun chooseTotal(rows: List<ReceiptRow>, upper: List<String>, from: Int, itemsTotal: Long): Long? {
        val candidates = (from until rows.size).mapNotNull { index ->
            val line = upper[index]
            val isTotal = "TOTAL" in line
            val isPayment = PAYMENT.any { it in line }
            if (!isTotal && !isPayment || NOT_TOTAL.any { it in line }) return@mapNotNull null
            rows[index].words.asReversed()
                .firstNotNullOfOrNull { word -> Amounts.parseCents(word.text)?.takeIf { it > 0 } }
        }
        if (candidates.isEmpty()) return null
        if (itemsTotal in candidates) return itemsTotal
        val counts = candidates.groupingBy { it }.eachCount()
        val best = counts.values.max()
        return candidates.first { counts[it] == best }
    }

    /**
     * If the items do not add up to the total, looks for one price that is a single
     * character away from the value that would make them add up ("11,99" → "1,99",
     * "2,39" → "2,35"). Among several candidates, the least confident OCR read wins.
     * The original value is kept so the review screen can show the correction.
     */
    private fun correctSingleMisread(items: List<ParsedItem>, total: Long): List<ParsedItem> {
        val difference = items.sumOf { it.netCents } - total
        if (difference == 0L) return items

        val candidate = items.withIndex()
            .filter { (_, item) ->
                val fixed = item.priceCents - difference
                fixed > 0 && editDistance(Amounts.format(item.priceCents), Amounts.format(fixed)) == 1
            }
            .minByOrNull { (_, item) -> item.priceConfidence }
            ?: return items

        val (index, item) = candidate
        return items.toMutableList().also {
            it[index] = item.copy(priceCents = item.priceCents - difference, correctedFromCents = item.priceCents)
        }
    }

    // endregion
}
