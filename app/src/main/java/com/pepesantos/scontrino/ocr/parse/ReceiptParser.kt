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
 *   the next one (Carrefour) or the previous one; "Cad 0,99 Pz. 2" (Lidl) always
 *   follows its item, so it is kept even if the unit price is one character off;
 *   a quantity line that also prints the line total ("2 UN X 4,99  9,98", Brazil)
 *   is the price of the name above it, and printed inside the item line
 *   ("LEITE 2 UN X 4,99  9,98") it is taken out of the name;
 * - a column of units left of the names ("1 CANÓNIGOS  1,19", Spain) is recognised
 *   when most item rows start with a small number, and that number is the quantity;
 * - a weight line "1,378 kg  3,15 €/kg  4,34" prices the name printed above it
 *   (or gives its weight to the item above with that price);
 * - a negative amount, or a "Sconto" line, is a discount on the previous item;
 * - the total is cross-checked with the payment lines and, where a VAT table with the
 *   taxable base is printed (Spain), with its TOTAL row (base + tax); if the items do
 *   not add up, a single misread price (one digit off) is corrected;
 * - where prices exclude taxes (US), a tax line whose amount, added to the items,
 *   makes the total is the tax added on top.
 *
 * Store-specific formats (other quantity wordings) will be added with more receipts.
 */
object ReceiptParser {

    /**
     * Column header words, as whole words: "DESCR." (Ecuador), "DESCRIZIONE",
     * "DESCRIPCION", "DESCRIÇÃO", "PREZZO". Whole words so that an item like
     * "LECHE DESCREMADA" is not mistaken for the header.
     */
    private val COLUMN_HEADER =
        Regex("""\b(DESCR(\.|IZIONE\b|IPCI[OÓ]N\b|IPTION\b|I[ÇC][ÃA]O\b|\b)|PREZZO\b)""")
    private val DISCOUNT = listOf("SCONT", "DESCUENTO", "RABATT", "REMISE", "DESCONTO")
    private val PAYMENT = listOf(
        "PAGAMENTO", "PAGATO", "BANCOMAT", "CONTANTI", "IMPORTO",
        "EFECTIVO", "TARJETA", "VISA", "MASTERCARD",
        // Portuguese: "VALOR A PAGAR", "VALOR PAGO", cash, card, Portuguese debit network.
        "PAGAR", "PAGO", "DINHEIRO", "CARTÃO", "CARTAO", "MULTIBANCO", "NUMERÁRIO", "NUMERARIO",
        // German: "gegeben bar" (cash given), card payment, German debit network.
        "GEGEBEN", "KARTENZAHLUNG", "GIROCARD", "EC-KARTE",
        // French: bank card, cash, "paiement".
        "CARTE BANCAIRE", "CARTE BLEUE", "ESPECES", "ESPÈCES", "PAIEMENT",
    )

    /**
     * Lines that close the item list (the total), by language:
     * - Italian "TOTALE", Spanish "TOTAL (€)", Portuguese "TOTAL", "TOTAL A PAGAR",
     *   "VALOR TOTAL", French "TOTAL", "TOTAL TTC", English "TOTAL": all contain "TOTAL";
     * - German "Summe", "zu zahlen";
     * - French "NET À PAYER" (often printed without "TOTAL").
     */
    private val TOTAL = listOf("TOTAL", "SUMME", "ZU ZAHLEN", "A PAYER", "À PAYER")

    /**
     * Subtotals: they close the item list but are not what was paid. Italian "SUBTOTALE",
     * Spanish/Portuguese/English "SUBTOTAL", French "SOUS-TOTAL", German "Zwischensumme".
     */
    private val SUBTOTAL = listOf("SUBTOTAL", "SOUS-TOTAL", "SOUS TOTAL", "ZWISCHENSUMME")

    /** Words of a price line that make its amount the price of one unit ("Einzelpreis"). */
    private val UNIT_PRICE = listOf("EINZELPREIS")

    /** A count before the name: "1 * IBU 400 AKUT", "2 x COLA" (Germany). */
    private val COUNT_SIGN = Regex("""^[X×*]$""")

    /**
     * Lines with an amount that is not the total: VAT breakdown, taxes, change, savings,
     * item count ("QTD. TOTAL DE ITENS"), and the Brazilian tax notice ("Tributos Totais
     * Incidentes", included in the prices).
     */
    private val NOT_TOTAL = listOf(
        "IVA", "TAX", "IMPUESTO", "RESTO", "CAMBIO", "VUELTO", "CHANGE", "SCONT", "RISPARMI",
        "TROCO", "TRIBUTOS", "DESCONTO", "POUPAN", "ITENS",
        // German: VAT, net sum ("Nettosumme"), change ("zurück", "Rückgeld").
        "MWST", "NETTO", "ZURÜCK", "ZURUCK", "RÜCKGELD", "RUCKGELD",
        // French: VAT, total before VAT ("TOTAL HT"), change ("rendu", "monnaie").
        "TVA", "TOTAL HT", "RENDU", "MONNAIE",
    )

    /** Header of a VAT table with the taxable base: "IVA  BASE IMPONIBLE (€)  CUOTA (€)". */
    private val VAT_TABLE_HEADER = listOf("IMPONIBLE", "BASE IMP")

    /** Tax lines: VAT included in the prices ("DI CUI IVA") or sales tax added to them. */
    private val TAX_LINE = listOf("TAX", "IMPUESTO", "IVA", "MWST", "TVA")

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
    private val QUANTITY = Regex("""^(\d{1,3})(?:[.,]0{0,3})?\s*(?:$UNITS)?\.?\s*[X×*]{1,2}\s*(\S+)""")

    /** Unit words after a count: "2 UN", "1UN", "3 UND", "2 PÇ" (Brazil, Portugal). */
    private const val UNITS = "UNID|UND|UN|PÇ|PC|PZ"

    /**
     * A quantity at the end of an item's name, before its price: "LEITE 2 UN X 4,99",
     * "ARROZ 1UN X 22,90", "LEITE 2 UN 4,99" (Brazilian QTD / UN / VL UNIT columns).
     */
    private val INLINE_QUANTITY =
        Regex("""\s(\d{1,3})(?:[.,]0{0,3})?\s*(?:(?:$UNITS)\.?\s*[X×*]?|[X×*])\s*(\S+)$""")
    /** A weight in kilograms at the start of a line: "1,378 kg", "0.456KG". */
    private val WEIGHT = Regex("""^(\d{1,3})[.,](\d{3})\s*KG\b""")

    /** The units column printed before the name: "1 CANÓNIGOS", "2 LECHE ENTERA". */
    private val LEADING_COUNT = Regex("""^\d{1,2}$""")

    /** Words after a number that make it a quantity line, not a units column. */
    private val QUANTITY_SIGN = Regex("""^(?:[X×*]{1,2}|(?:$UNITS)\.?)$""")

    /** "Cad 0,99 Pz. 2" (cadauno, pezzi), printed under the item it belongs to. */
    private val QUANTITY_EACH = Regex("""^CAD\.?\s+(\S+)\s+PZ\.?\s*(\d{1,3})(?!\d)""")
    /**
     * The store's tax id, by country. Only labels that identify the store: on
     * Ecuadorian receipts "RUC/CED/PAS" is the customer and "RUC Proveedor" the
     * e-invoicing provider, so "RUC" must be followed directly by the colon.
     */
    private val TAX_IDS = listOf(
        Regex("""\bRUC\s*:\s*(\d{13})\b"""), // Ecuador, RUC
        Regex("""\b(?:CIF|NIF)\s*:?\s*([A-Z][-\s]?\d{7}[A-Z0-9])\b"""), // Spain
        // Brazil, CNPJ "12.345.678/0001-90" (the customer's is a CPF).
        Regex("""\bCNPJ\s*[:.]?\s*(\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2})"""),
    )

    /**
     * Germany, "USt-IdNr.: DE123456789" (also "USt-ID", "UStID-Nr."). The digits may
     * come with a slashed zero read as "O". Labelled, so not checked any further.
     */
    private val GERMAN_VAT_ID =
        Regex("""\bUST[-.\s]?ID[-.\s]*(?:NR|NUMMER)?\.?\s*:?\s*DE\s?([0-9O]{3}\s?[0-9O]{3}\s?[0-9O]{3})(?![0-9O])""")

    /** Portugal: "NIF: 500 100 144", "Contribuinte n.º 500100144", "NIPC". */
    private val PORTUGUESE_NIF = Regex("""\b(?:NIF|NIPC|CONTRIBUINTE)\b\D{0,12}?(\d{3}\s?\d{3}\s?\d{3})(?!\d)""")

    /** Lines with the customer's tax id, printed on Portuguese receipts as well. */
    private val CUSTOMER = listOf("CLIENTE", "ADQUIRENTE", "CONSUMIDOR")

    /** NIF of the "final consumer" (no customer id given). */
    private const val FINAL_CONSUMER_NIF = "999999990"

    fun parse(rows: List<ReceiptRow>): ParsedReceipt {
        val upper = rows.map { it.text.uppercase(Locale.ROOT) }

        val columnHeader = upper.indexOfFirst { COLUMN_HEADER.containsMatchIn(it) }
        val columns = Columns.of(rows.getOrNull(columnHeader))
        val start = if (columnHeader >= 0) columnHeader + 1 else 0
        val end = (start until rows.size).firstOrNull { index -> TOTAL.any { it in upper[index] } } ?: rows.size
        val countColumn = hasCountColumn(rows, start, end)

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
            // The units column is taken out first: it lies left of the description.
            val counted = if (countColumn) leadingCount(rows[index]) else null
            val row = columns.trim(counted?.second ?: rows[index]) ?: continue
            if (WEIGHT.containsMatchIn(row.text.uppercase(Locale.ROOT).trim())) {
                val weight = readWeight(row, index)
                val previous = items.lastOrNull()
                val name = pendingName
                when {
                    weight == null -> unparsed += row.text
                    name != null -> {
                        pendingName = null
                        addItem(weight.toItem(name))
                    }
                    previous != null && previous.weightGrams == null && previous.priceCents == weight.lineCents ->
                        items[items.lastIndex] = previous.copy(
                            weightGrams = weight.grams,
                            extraRows = previous.extraRows + index,
                        )
                    else -> unparsed += row.text
                }
                continue
            }
            readQuantityEach(row, index, previousItem = items.lastIndex)?.let { quantity ->
                val previous = items.getOrNull(quantity.previousItem)
                if (previous != null && quantity.nearlyFits(previous)) {
                    items[quantity.previousItem] = quantity.applyTo(previous)
                } else {
                    unparsed += quantity.text
                }
                continue
            }
            readQuantity(row, index, columns, previousItem = items.lastIndex)?.let { quantity ->
                val name = pendingName
                if (name != null && quantity.lineCents != null) {
                    // "LEITE INTEGRAL" / "2 UN X 4,99  9,98": the price of the name above.
                    pendingName = null
                    addItem(quantity.toItem(name, index))
                } else {
                    pendingQuantity?.let(::settle)
                    pendingQuantity = quantity
                }
                continue
            }
            val line = readLine(row, index, vatLegend, columns, count = counted?.first)
            when {
                line == null -> {
                    pendingName?.let { unparsed += it.text }
                    pendingName = cleanName(row.words, rowIndex = index).takeIf { it.isValid }
                    if (pendingName == null && row.text.isNotBlank()) unparsed += row.text
                }

                line.isDiscount -> {
                    val last = items.lastIndex
                    if (last >= 0) {
                        items[last] = items[last].let {
                            it.copy(discountCents = it.discountCents + abs(line.cents), extraRows = it.extraRows + index)
                        }
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
                    // "03045316  Einzelpreis  EUR 9,45": the digits are the article code.
                    val code = line.name.text.takeIf { text -> text.length >= 3 && text.all { it.isDigit() } }
                    addItem(line.toItem(pendingName.withCode(code)))
                    pendingName = null
                }

                else -> unparsed += row.text
            }
        }
        pendingName?.let { unparsed += it.text }
        pendingQuantity?.let(::settle)

        val itemsTotal = items.sumOf { it.netCents }
        val vatTable = vatTableTotal(rows, upper, from = end)
        val totals = totalCandidates(rows, upper, from = end, skip = vatTable?.first)
        val paid = totalCandidates(rows, upper, from = end, includeSubtotals = false, skip = vatTable?.first)
        val tax = addedTax(itemsTotal, paid, taxAmounts(rows, upper, from = end))
        val total = if (tax != null) itemsTotal + tax else chooseTotal(totals, itemsTotal, vatTable?.second)
        val checkedItems = if (total != null && tax == null) correctSingleMisread(items, total) else items

        return ParsedReceipt(
            items = checkedItems,
            totalCents = total,
            date = ReceiptDateParser.find(rows.map { it.text }),
            vatNumber = upper.firstNotNullOfOrNull { line ->
                ItalianVatNumber.find(line)
                    ?: TAX_IDS.firstNotNullOfOrNull { regex ->
                        regex.find(line)?.groupValues?.get(1)?.filter { it.isLetterOrDigit() }
                    }
                    ?: portugueseNif(line)
                    ?: germanVatId(line)
                    ?: SpanishCif.findAlone(line)
            },
            headerLines = rows.take(if (columnHeader >= 0) columnHeader else start)
                .map { it.text }
                .filter { it.isNotBlank() },
            unparsedRows = unparsed,
            footerLines = rows.drop(end).map { it.text }.filter { it.isNotBlank() },
            taxCents = tax,
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
        /** Units printed inside the line ("2 UN X 4,99"), 1 if none. */
        val quantity: Int = 1,
        /** The amount is the price of one unit ("Einzelpreis"), not the line total. */
        val isUnitPrice: Boolean = false,
    ) {
        fun toItem(name: Name) = ParsedItem(
            name = name.text,
            code = name.code,
            // "2 * NAME" / "Einzelpreis 4,50": the line total is both units.
            priceCents = if (isUnitPrice) cents * name.count else cents,
            quantity = if (name.count > 1) name.count else quantity,
            vatCode = vatCode,
            vatRate = vatRate,
            priceConfidence = priceWord.confidence,
            nameConfidence = name.confidence,
            rowIndex = rowIndex,
            extraRows = listOfNotNull(name.rowIndex),
        )
    }

    /**
     * Reads a row that contains an amount, or returns null if it has none. [count] is
     * the units column, already taken out of the row.
     */
    private fun readLine(
        row: ReceiptRow,
        index: Int,
        vatLegend: Map<String, Double>,
        columns: Columns,
        count: Int? = null,
    ): Line? {
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
        val inline = if (count == null) inlineQuantity(words.subList(0, priceIndex), cents) else null
        val nameWords = (if (priceWord in firstSegment) words.subList(0, priceIndex) else firstSegment)
            .filterNot { word -> inline?.second?.any { it === word } == true }
            // With a units column, the unit price sits between the name and the price.
            .filterNot { word -> count != null && Amounts.parseCents(word.text) != null }
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
            quantity = count ?: inline?.first ?: 1,
            isUnitPrice = row.text.uppercase(Locale.ROOT).let { text -> UNIT_PRICE.any { it in text } },
        )
    }

    /**
     * Whether the item rows start with a units column ("1 CANÓNIGOS  1,19"): most
     * rows with a name between [start] and [end] begin with a small number.
     */
    private fun hasCountColumn(rows: List<ReceiptRow>, start: Int, end: Int): Boolean {
        val named = (start until end).map { rows[it] }
            .filter { row -> row.words.any { word -> word.text.count { it.isLetter() } >= 2 } }
        val counted = named.count { leadingCount(it) != null }
        return counted >= 3 && counted * 5 >= named.size * 3
    }

    /**
     * The units printed before the name ("1 CANÓNIGOS", "1 12 HUEVOS") and the row
     * without them; not a quantity line ("2 x 0,79", "2 UN") nor a bare amount.
     */
    private fun leadingCount(row: ReceiptRow): Pair<Int, ReceiptRow>? {
        val words = row.words
        // Specks before the number (":", "|") are skipped.
        val first = words.indexOfFirst { word -> word.text.any { it.isLetterOrDigit() } }
        if (first < 0) return null
        val countWord = words[first]
        val count = LEADING_COUNT.matchEntire(countWord.text)?.value?.toInt()?.takeIf { it >= 1 } ?: return null
        val next = words.getOrNull(first + 1) ?: return null
        if (QUANTITY_SIGN.matches(next.text.uppercase(Locale.ROOT)) || Amounts.parseCents(next.text) != null) return null
        val segments = row.segmentWords
            .map { segment -> segment.filterNot { it === countWord } }
            .filter { it.isNotEmpty() }
        return count to ReceiptRow(segments.flatten(), segments)
    }

    /** "1,378 kg  3,15 €/kg  4,34": [grams] sold for [lineCents], the last amount. */
    private class Weight(val grams: Double, val lineCents: Long, val confidence: Float, val rowIndex: Int) {
        fun toItem(name: Name) = ParsedItem(
            name = name.text,
            code = name.code,
            priceCents = lineCents,
            weightGrams = grams,
            priceConfidence = confidence,
            nameConfidence = name.confidence,
            rowIndex = rowIndex,
            extraRows = listOfNotNull(name.rowIndex),
        )
    }

    /** Reads a weight line, or null if it has no positive line total. */
    private fun readWeight(row: ReceiptRow, index: Int): Weight? {
        val match = WEIGHT.find(row.text.uppercase(Locale.ROOT).trim()) ?: return null
        val (kilos, thousandths) = match.destructured
        val grams = kilos.toInt() * 1000.0 + thousandths.toInt()
        if (grams <= 0.0) return null
        val lineWord = row.words.lastOrNull { Amounts.parseCents(it.text) != null } ?: return null
        val lineCents = Amounts.parseCents(lineWord.text)?.takeIf { it > 0 } ?: return null
        return Weight(grams, lineCents, lineWord.confidence, index)
    }

    /**
     * A quantity at the end of [words] ("LEITE 2 UN X 4,99") whose units make the line
     * price [cents]: the count and the words it takes, to leave them out of the name.
     */
    private fun inlineQuantity(words: List<OcrWord>, cents: Long): Pair<Int, List<OcrWord>>? {
        if (words.size < 2 || cents <= 0) return null
        val text = words.joinToString(" ") { it.text }.uppercase(Locale.ROOT)
        val match = INLINE_QUANTITY.find(text) ?: return null
        val count = match.groupValues[1].toInt().takeIf { it >= 1 } ?: return null
        val unitCents = Amounts.parseCents(match.groupValues[2]) ?: return null
        if (count * unitCents != cents) return null
        val taken = match.value.trim().split(Regex("""\s+""")).size
        if (taken >= words.size) return null
        return count to words.takeLast(taken)
    }

    /** A line like "2 x 0,79": [count] units at [unitCents] each. */
    private class Quantity(
        val count: Int,
        val unitCents: Long,
        val vatRate: Double?,
        val text: String,
        /** Row of this line. */
        val rowIndex: Int,
        /** Index of the item read just before this line, or -1. */
        val previousItem: Int,
        /** The line total printed after the unit price ("2 UN X 4,99  9,98"), if any. */
        val lineCents: Long? = null,
        val lineConfidence: Float = 0f,
    ) {
        /** The item of a name printed above this line, priced with [lineCents]. */
        fun toItem(name: Name, rowIndex: Int) = ParsedItem(
            name = name.text,
            code = name.code,
            priceCents = lineCents ?: (count * unitCents),
            quantity = count,
            vatRate = vatRate,
            vatCode = vatRate?.let { "${it.toInt()}%" },
            priceConfidence = lineConfidence,
            nameConfidence = name.confidence,
            rowIndex = rowIndex,
            extraRows = listOfNotNull(name.rowIndex),
        )

        fun fits(item: ParsedItem) = item.quantity == 1 && item.priceCents == count * unitCents

        /**
         * [fits], or the unit price is one character away from the item's price
         * divided by [count] ("Cad 9,99 Pz. 2" under 1,98: a slashed zero read as 9).
         */
        fun nearlyFits(item: ParsedItem) = fits(item) || item.quantity == 1 &&
            item.priceCents > 0 && item.priceCents % count == 0L &&
            editDistance(Amounts.format(unitCents), Amounts.format(item.priceCents / count)) == 1

        fun applyTo(item: ParsedItem) = item.copy(
            quantity = count,
            // Carrefour prints the rate on the quantity line when it does not fit the item's.
            vatRate = item.vatRate ?: vatRate,
            vatCode = item.vatCode ?: vatRate?.let { "${it.toInt()}%" },
            extraRows = item.extraRows + rowIndex,
        )
    }

    private fun readQuantity(row: ReceiptRow, index: Int, columns: Columns, previousItem: Int): Quantity? {
        val text = row.words.joinToString(" ") { it.text }
        val match = QUANTITY.find(text.uppercase(Locale.ROOT).trim()) ?: return null
        val count = match.groupValues[1].toInt()
        val unitCents = Amounts.parseCents(match.groupValues[2])?.takeIf { it > 0 } ?: return null
        // "2 UN X 4,99  9,98": the last amount is the line total if the units make it.
        val lineWord = row.words.lastOrNull { Amounts.parseCents(it.text) != null }
            ?.takeIf { row.words.count { word -> Amounts.parseCents(word.text) != null } >= 2 }
            ?.takeIf { Amounts.parseCents(it.text) == count * unitCents }
        if (count < 2 && lineWord == null) return null
        return Quantity(
            count = count,
            unitCents = unitCents,
            vatRate = row.words.firstNotNullOfOrNull { columns.vatRate(it) },
            text = row.text,
            rowIndex = index,
            previousItem = previousItem,
            lineCents = lineWord?.let { Amounts.parseCents(it.text) },
            lineConfidence = lineWord?.confidence ?: 0f,
        )
    }

    private fun readQuantityEach(row: ReceiptRow, index: Int, previousItem: Int): Quantity? {
        val text = row.words.joinToString(" ") { it.text }
        val match = QUANTITY_EACH.find(text.uppercase(Locale.ROOT).trim()) ?: return null
        val unitCents = Amounts.parseCents(match.groupValues[1])?.takeIf { it > 0 } ?: return null
        val count = match.groupValues[2].toInt().takeIf { it >= 2 } ?: return null
        return Quantity(count, unitCents, vatRate = null, text = row.text, rowIndex = index, previousItem = previousItem)
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
                val price = words.indices.firstOrNull { index ->
                    listOf("PREZZO", "PRECIO", "PREÇO", "PRECO").any { it in upper[index] }
                }
                val vat = words.indices.firstOrNull { VAT_HEADER.matches(upper[it]) }
                return Columns(
                    descriptionLeft = description?.let { words[it].box.left },
                    priceCenter = center(price?.let(words::get)),
                    vatCenter = center(vat?.let(words::get)),
                )
            }
        }
    }

    /**
     * [rowIndex]: the row of a name printed apart from its price, null if on the same row.
     * [count]: units printed before the name ("1 * IBU 400"), 1 if none.
     */
    private class Name(
        val text: String,
        val code: String?,
        val confidence: Float,
        val rowIndex: Int? = null,
        val count: Int = 1,
    ) {
        /** A product name needs some letters; "." or "00" is OCR noise. */
        val isValid: Boolean get() = text.count { it.isLetter() } >= 2

        /** This name with the article code [code] printed on another row, if it has none. */
        fun withCode(code: String?) =
            if (this.code != null || code == null) this else Name(text, code, confidence, rowIndex, count)
    }

    private fun cleanName(allWords: List<OcrWord>, rowIndex: Int? = null): Name {
        // "1 * IBU 400 AKUT": the count and its sign are not part of the name.
        val count = allWords.firstOrNull()?.text?.takeIf { text -> text.length <= 3 && text.all { it.isDigit() } }
            ?.takeIf { allWords.size > 2 && COUNT_SIGN.matches(allWords[1].text.uppercase(Locale.ROOT)) }
            ?.toInt()
            ?.takeIf { it >= 1 }
        val words = if (count != null) allWords.drop(2) else allWords
        val tokens = words.map { it.text }
            .filter { token -> token.any { it.isLetterOrDigit() } }
            // Receipts print names in capitals; lone lowercase letters are specks.
            .filterNot { it.length == 1 && it[0].isLowerCase() }
            .filter { Amounts.parsePercent(it) == null }
        // Some stores print an article code first: "353406 CONT.LEGGEREZZA G"; Brazilian
        // ones the item number and the barcode: "001 7891000100103 LEITE INTEGRAL".
        val codes = tokens.takeWhile { token -> token.length >= 3 && token.all { it.isDigit() } }
            .take(2)
            .takeIf { it.size < tokens.size }
            .orEmpty()
        val hasCode = codes.isNotEmpty()
        val nameTokens = tokens.drop(codes.size)
        // Confidence of the words that make the name itself, not of codes or specks.
        val nameWords = words.filter { word -> word.text.any { it.isLetter() } }
        return Name(
            text = nameTokens.joinToString(" ").trim { it in ".,;:|-_=" || it.isWhitespace() },
            code = if (hasCode) codes.last() else null,
            confidence = if (nameWords.isEmpty()) 0f else nameWords.map { it.confidence }.average().toFloat(),
            rowIndex = rowIndex,
            count = count ?: 1,
        )
    }

    // endregion

    /**
     * The Portuguese NIF of the store, if [line] has one: not the customer's, nor the
     * "final consumer" placeholder, and with a valid check digit (mod 11).
     */
    private fun portugueseNif(line: String): String? {
        if (CUSTOMER.any { it in line }) return null
        val nif = PORTUGUESE_NIF.find(line)?.groupValues?.get(1)?.filter { it.isDigit() } ?: return null
        if (nif == FINAL_CONSUMER_NIF) return null
        val sum = (0 until 8).sumOf { (nif[it] - '0') * (9 - it) }
        val check = (11 - sum % 11).let { if (it >= 10) 0 else it }
        return nif.takeIf { nif[8] - '0' == check }
    }

    /** The German VAT id of the store ("DE" and nine digits), if [line] has one. */
    private fun germanVatId(line: String): String? =
        GERMAN_VAT_ID.find(line)?.groupValues?.get(1)
            ?.filterNot { it.isWhitespace() }
            ?.replace('O', '0')
            ?.let { "DE$it" }

    // region Total

    /** The last amount of [row], if positive. */
    private fun lastAmount(row: ReceiptRow): Long? =
        row.words.asReversed().firstNotNullOfOrNull { word -> Amounts.parseCents(word.text)?.takeIf { it > 0 } }

    /**
     * Amounts of the total and payment lines from row [from] on, in order; without
     * [includeSubtotals], only what was paid ("SUBTOTAL", "SUBTOTALE" left out).
     */
    private fun totalCandidates(
        rows: List<ReceiptRow>,
        upper: List<String>,
        from: Int,
        includeSubtotals: Boolean = true,
        /** The VAT table's TOTAL row, whose last amount is the tax, not the total. */
        skip: Int? = null,
    ): List<Long> =
        (from until rows.size).filter { it != skip }.mapNotNull { index ->
            val line = upper[index]
            val isTotal = TOTAL.any { it in line } && (includeSubtotals || SUBTOTAL.none { it in line })
            val isPayment = PAYMENT.any { it in line }
            if (!isTotal && !isPayment || NOT_TOTAL.any { it in line }) null else lastAmount(rows[index])
        }

    /** Amounts of the tax lines from row [from] on: VAT included, or tax to add. */
    private fun taxAmounts(rows: List<ReceiptRow>, upper: List<String>, from: Int): List<Long> =
        (from until rows.size).mapNotNull { index ->
            if (TAX_LINE.any { it in upper[index] }) lastAmount(rows[index]) else null
        }

    /**
     * The tax added on top of the items (US sales tax): a tax line, or the sum of
     * several (state and city), that together with the items makes what was [paid].
     * Where VAT is included (Italy "DI CUI IVA 0,84", Ecuador "IVA 15%") the items
     * alone already make the total or the payment, so nothing is added. A subtotal
     * equal to the items does not count as paid: "SUBTOTAL 10,00 / TAX 0,80 / TOTAL
     * 10,80" has tax 0,80.
     */
    private fun addedTax(itemsTotal: Long, paid: List<Long>, taxes: List<Long>): Long? {
        if (itemsTotal <= 0 || taxes.isEmpty() || itemsTotal in paid) return null
        val options = if (taxes.size > 1) taxes + taxes.sum() else taxes
        return options.firstOrNull { tax -> itemsTotal + tax in paid }
    }

    /**
     * The TOTAL row of a VAT table with the taxable base ("TOTAL  45,72  3,01", Spain)
     * after row [from]: its index and base + tax, the total computed from small print.
     */
    private fun vatTableTotal(rows: List<ReceiptRow>, upper: List<String>, from: Int): Pair<Int, Long>? {
        val header = (from until rows.size).firstOrNull { index -> VAT_TABLE_HEADER.any { it in upper[index] } }
            ?: return null
        val index = (header + 1 until rows.size).firstOrNull { "TOTAL" in upper[it] } ?: return null
        val amounts = rows[index].words.mapNotNull { word -> Amounts.parseCents(word.text)?.takeIf { it > 0 } }
        if (amounts.size < 2) return null
        val (base, tax) = amounts.takeLast(2)
        return if (base > tax) index to base + tax else null
    }

    /**
     * The printed total is cross-checked with the payment lines, since a large or
     * condensed total font is often misread ("1768", "19,9"). Preference: an amount
     * equal to the items' sum, then the most repeated one, then the total line.
     * Where the payment line shares the large font (Mercadona: "48,73" read "48,13"
     * on both), the VAT table's base + tax [vatTable] wins if it is one character
     * away from that choice.
     */
    private fun chooseTotal(candidates: List<Long>, itemsTotal: Long, vatTable: Long? = null): Long? {
        if (itemsTotal in candidates || itemsTotal == vatTable) return itemsTotal
        val printed = if (candidates.isEmpty()) null else {
            val counts = candidates.groupingBy { it }.eachCount()
            val best = counts.values.max()
            candidates.first { counts[it] == best }
        }
        if (vatTable == null || printed == vatTable) return printed
        if (printed == null) return vatTable
        return if (editDistance(Amounts.format(printed), Amounts.format(vatTable)) == 1) vatTable else printed
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
