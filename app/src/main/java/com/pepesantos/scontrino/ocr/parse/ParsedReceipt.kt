package com.pepesantos.scontrino.ocr.parse

/**
 * Result of interpreting the OCR rows of a receipt. Amounts are in cents to keep
 * sums exact. Everything here is a proposal for the user to review, never final.
 */
data class ParsedReceipt(
    val items: List<ParsedItem>,
    /** Amount paid according to the receipt (total, or payment lines if unreadable). */
    val totalCents: Long?,
    val date: ReceiptDate?,
    /** Store VAT number (Italian P.IVA), useful to recognise the store. */
    val vatNumber: String?,
    /** Rows above the item list (store name, address...). */
    val headerLines: List<String>,
    /** Rows inside the item list that could not be interpreted. */
    val unparsedRows: List<String>,
    /** Rows from the first total line on (totals, payment, date, footer). */
    val footerLines: List<String> = emptyList(),
) {
    val itemsTotalCents: Long get() = items.sumOf { it.netCents }

    /** All the text that is not an item: where the store name usually appears. */
    val nonItemLines: List<String> get() = headerLines + unparsedRows + footerLines

    /** True when the items, after discounts, add up exactly to the total. */
    val isConsistent: Boolean get() = totalCents != null && totalCents == itemsTotalCents
}

data class ParsedItem(
    val name: String,
    /** Article code printed before the name by some stores (e.g. Bennet "353406"). */
    val code: String?,
    /** Line total as printed: for [quantity] > 1, the price of all the units. */
    val priceCents: Long,
    /**
     * Units bought, from a quantity line such as "2 x 0,79" that matches [priceCents]
     * exactly. 1 when the receipt prints none.
     */
    val quantity: Int = 1,
    /** Sum of the discount lines that follow the item, as a positive amount. */
    val discountCents: Long = 0,
    /** VAT as printed: a rate ("22%") or a letter code ("C"). */
    val vatCode: String? = null,
    /** VAT rate in percent, from the item line or the receipt's letter legend. */
    val vatRate: Double? = null,
    /** OCR confidence of the price (0..100). */
    val priceConfidence: Float,
    /**
     * Mean OCR confidence (0..100) of the words of the name. The name is what links
     * an item to the user's products, aliases and price history.
     */
    val nameConfidence: Float = 0f,
    /** Original price if it was auto-corrected so that the receipt adds up. */
    val correctedFromCents: Long? = null,
    /** Index of the source row, to point back at the photo. */
    val rowIndex: Int,
) {
    val netCents: Long get() = priceCents - discountCents
}

data class ReceiptDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int? = null,
    val minute: Int? = null,
) {
    override fun toString(): String {
        val date = "%02d/%02d/%04d".format(day, month, year)
        return if (hour != null && minute != null) "$date %02d:%02d".format(hour, minute) else date
    }
}
