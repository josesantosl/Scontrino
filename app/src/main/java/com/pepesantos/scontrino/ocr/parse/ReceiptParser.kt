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
 *   by a VAT letter, and may contain a VAT rate; when the header has a VAT column,
 *   a rate printed as a plain number under it ("2,19  22,00") is not the price;
 * - words left of the header's description column are not part of the receipt
 *   (whatever lies next to it in the photo);
 * - a quantity line "2 x 0,79" belongs to the item whose price it multiplies to,
 *   the next one (Carrefour) or the previous one;
 * - a negative amount, or a "Sconto" line, is a discount on the previous item;
 * - the total is cross-checked with the payment lines, and if the items do not add
 *   up, a single misread price (one digit off) is corrected.
 *
 * Store-specific formats (weights, other quantity wordings) will be added with more receipts.
 */
object ReceiptParser {

    /**
     * Column header words, as whole words: "DESCR." (Ecuador), "DESCRIZIONE",
     * "DESCRIPCION", "PREZZO". Whole words so that an item like "LECHE DESCREMADA"
     * is not mistaken for the header.
     */
    private val COLUMN_HEADER = Regex("""\b(DESCR(\.|IZIONE\b|IPCI[OÓ]N\b|IPTION\b|\b)|PREZZO\b)""")
    private val DISCOUNT = listOf("SCONT", "DESCUENTO", "RABATT", "REMISE")
    private val PAYMENT = listOf(
        "PAGAMENTO", "PAGATO", "BANCOMAT", "CONTANTI", "IMPORTO",
        "EFECTIVO", "TARJETA", "VISA", "MASTERCARD",
    )

    /** Lines with an amount that is not the total: VAT breakdown, change, savings. */
    private val NOT_TOTAL = listOf("IVA", "RESTO", "CAMBIO", "VUELTO", "CHANGE", "SCONT", "RISPARMI")

    private val VAT_LEGEND = Regex("""^([A-F])\s*[:.]?\s*IVA\s*(\d{1,2}(?:[,.]\d{1,2})?)\s*%""")
    private val VAT_LETTER = Regex("""^[A-F]$""")
    /** "IVA", "%IVA", "IVA%" and misreads such as "XIVA" in the column header. */
    private val VAT_HEADER = Regex("""^.?IVA.?$""")
    /** Highest VAT rate that a number in the VAT column can be (cents of a percent). */
    private const val MAX_VAT_RATE_CENTS = 3000L
    /**
     * "2 x 0,79", "3 X 1,20", "2 * 0,50", "2x0,79" at the start of a line, tolerating
     * the usual misreads of the thin print: a speck after the count ("2.") and a
     * doubled sign ("xX").
     */
    private val QUANTITY = Regex("""^(\d{1,3})[.,]?\s*[X×*]{1,2}\s*(\S+)""")
    /**
     * The store's tax id, by country. Only labels that identify the store: on
     * Ecuadorian receipts "RUC/CED/PAS" is the customer and "RUC Proveedor" the
     * e-invoicing provider, so "RUC" must be followed directly by the colon.
     */
    private val TAX_IDS = listOf(
        Regex("""P\.?\s*I\.?\s*V\.?\s*A\.?\s*:?\s*(\d{11})"""), // Italy, P.IVA
        Regex("""\bRUC\s*:\s*(\d{13})\b"""), // Ecuador, RUC
        Regex("""\b(?:CIF|NIF)\s*:?\s*([A-Z]\d{7}[A-Z0-9])\b"""), // Spain
    )

    fun parse(rows: List<ReceiptRow>): ParsedReceipt {
        val upper = rows.map { it.text.uppercase(Locale.ROOT) }

        val columnHeader = upper.indexOfFirst { COLUMN_HEADER.containsMatchIn(it) }
        val columns = Columns.of(rows.getOrNull(columnHeader))
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
        // A quantity line waiting for the item it multiplies to.
        var pendingQuantity: Quantity? = null

        /** A quantity line that did not match the next item: the previous one, or nothing. */
        fun settle(quantity: Quantity) {
            val previous = items.getOrNull(quantity.previousItem)
            if (previous != null && quantity.fits(previous)) {
                items[quantity.previousItem] = quantity.applyTo(previous)
            } else {
                unparsed += quantity.text
            }
        }

        fun addItem(item: ParsedItem) {
            val quantity = pendingQuantity
            pendingQuantity = null
            if (quantity != null && quantity.fits(item)) {
                items += quantity.applyTo(item)
            } else {
                items += item
                quantity?.let(::settle)
            }
        }

        for (index in start until end) {
            val row = columns.trim(rows[index]) ?: continue
            readQuantity(row, columns, previousItem = items.lastIndex)?.let { quantity ->
                pendingQuantity?.let(::settle)
                pendingQuantity = quantity
                continue
            }
            val line = readLine(row, index, vatLegend, columns)
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
                    addItem(line.toItem(line.name))
                }

                pendingName != null && line.cents > 0 -> {
                    addItem(line.toItem(pendingName))
                    pendingName = null
                }

                else -> unparsed += row.text
            }
        }
        pendingName?.let { unparsed += it.text }
        pendingQuantity?.let(::settle)

        val itemsTotal = items.sumOf { it.netCents }
        val total = chooseTotal(rows, upper, from = end, itemsTotal = itemsTotal)
        val checkedItems = if (total != null) correctSingleMisread(items, total) else items

        return ParsedReceipt(
            items = checkedItems,
            totalCents = total,
            date = ReceiptDateParser.find(rows.map { it.text }),
            vatNumber = upper.firstNotNullOfOrNull { line ->
                TAX_IDS.firstNotNullOfOrNull { it.find(line)?.groupValues?.get(1) }
            },
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
            nameConfidence = name.confidence,
            rowIndex = rowIndex,
        )
    }

    /** Reads a row that contains an amount, or returns null if it has none. */
    private fun readLine(row: ReceiptRow, index: Int, vatLegend: Map<String, Double>, columns: Columns): Line? {
        val words = row.words
        val amounts = words.indices.filter { Amounts.parseCents(words[it].text) != null }
        if (amounts.isEmpty()) return null
        // "2,19  22,00": with a VAT column, the rate is the last amount, not the price.
        val columnRate = columns.vatRate(words[amounts.last()]).takeIf { amounts.size > 1 }
        val priceIndex = if (columnRate != null) amounts[amounts.size - 2] else amounts.last()
        val priceWord = words[priceIndex]
        val cents = Amounts.parseCents(priceWord.text)!!

        val vatLetter = words.getOrNull(priceIndex + 1)?.text?.takeIf { VAT_LETTER.matches(it) }
        val percentWord = words.subList(0, priceIndex).lastOrNull { Amounts.parsePercent(it.text) != null }
        val vatRate = percentWord?.let { Amounts.parsePercent(it.text) }
            ?: columnRate
            ?: vatLetter?.let { vatLegend[it] }

        // The name is the first column, unless the price shares it (short rows).
        val firstSegment = row.segmentWords.first()
        val nameWords = if (priceWord in firstSegment) words.subList(0, priceIndex) else firstSegment
        val name = cleanName(nameWords)

        val upperName = name.text.uppercase(Locale.ROOT)
        return Line(
            cents = cents,
            priceWord = priceWord,
            name = name,
            vatCode = vatLetter
                ?: percentWord?.text?.let { "${Amounts.parsePercent(it)?.toInt()}%" }
                ?: columnRate?.let { "${it.toInt()}%" },
            vatRate = vatRate,
            isDiscount = cents < 0 || DISCOUNT.any { it in upperName },
            rowIndex = index,
        )
    }

    /** A line like "2 x 0,79": [count] units at [unitCents] each. */
    private class Quantity(
        val count: Int,
        val unitCents: Long,
        val vatRate: Double?,
        val text: String,
        /** Index of the item read just before this line, or -1. */
        val previousItem: Int,
    ) {
        fun fits(item: ParsedItem) = item.quantity == 1 && item.priceCents == count * unitCents

        fun applyTo(item: ParsedItem) = item.copy(
            quantity = count,
            // Carrefour prints the rate on the quantity line when it does not fit the item's.
            vatRate = item.vatRate ?: vatRate,
            vatCode = item.vatCode ?: vatRate?.let { "${it.toInt()}%" },
        )
    }

    private fun readQuantity(row: ReceiptRow, columns: Columns, previousItem: Int): Quantity? {
        val text = row.words.joinToString(" ") { it.text }
        val match = QUANTITY.find(text.uppercase(Locale.ROOT).trim()) ?: return null
        val count = match.groupValues[1].toInt()
        val unitCents = Amounts.parseCents(match.groupValues[2])?.takeIf { it > 0 } ?: return null
        if (count < 2) return null
        return Quantity(
            count = count,
            unitCents = unitCents,
            vatRate = row.words.firstNotNullOfOrNull { columns.vatRate(it) },
            text = row.text,
            previousItem = previousItem,
        )
    }

    /**
     * Where the column header puts the description, price and VAT columns. Lets the
     * parser ignore what lies left of the receipt in the photo and tell a VAT rate
     * printed as a number from the price.
     */
    private class Columns(
        private val descriptionLeft: Int?,
        private val priceCenter: Double?,
        private val vatCenter: Double?,
    ) {
        /** The row without words left of the description column; null if nothing is left. */
        fun trim(row: ReceiptRow): ReceiptRow? {
            val left = descriptionLeft ?: return row
            val segments = row.segmentWords
                .map { segment -> segment.filter { it.box.right >= left } }
                .filter { it.isNotEmpty() }
            if (segments.isEmpty()) return null
            return ReceiptRow(segments.flatten(), segments)
        }

        /** The VAT rate if [word] is a plain number under the VAT column ("22,00"). */
        fun vatRate(word: OcrWord): Double? {
            val vat = vatCenter ?: return null
            val price = priceCenter ?: return null
            val cents = Amounts.parseCents(word.text)?.takeIf { it in 0..MAX_VAT_RATE_CENTS } ?: return null
            val center = (word.box.left + word.box.right) / 2.0
            return if (abs(center - vat) < abs(center - price)) cents / 100.0 else null
        }

        companion object {
            private val NONE = Columns(null, null, null)

            fun of(header: ReceiptRow?): Columns {
                val words = header?.words ?: return NONE
                fun center(word: OcrWord?) = word?.let { (it.box.left + it.box.right) / 2.0 }
                val upper = words.map { it.text.uppercase(Locale.ROOT) }
                val description = words.indices.firstOrNull { upper[it].startsWith("DESCR") }
                val price = words.indices.firstOrNull { "PREZZO" in upper[it] || "PRECIO" in upper[it] }
                val vat = words.indices.firstOrNull { VAT_HEADER.matches(upper[it]) }
                return Columns(
                    descriptionLeft = description?.let { words[it].box.left },
                    priceCenter = center(price?.let(words::get)),
                    vatCenter = center(vat?.let(words::get)),
                )
            }
        }
    }

    private class Name(val text: String, val code: String?, val confidence: Float) {
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
        // Confidence of the words that make the name itself, not of codes or specks.
        val nameWords = words.filter { word -> word.text.any { it.isLetter() } }
        return Name(
            text = nameTokens.joinToString(" ").trim { it in ".,;:|-_=" || it.isWhitespace() },
            code = if (hasCode) tokens.first() else null,
            confidence = if (nameWords.isEmpty()) 0f else nameWords.map { it.confidence }.average().toFloat(),
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
