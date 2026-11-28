package com.pepesantos.scontrino.ocr

import com.pepesantos.scontrino.ocr.parse.ReceiptDate
import java.text.Normalizer
import java.util.Locale

/**
 * Base name of the files shared from the OCR lab, `store-yyyy-mm-dd-ocr`, so several
 * receipts sent by a tester can be told apart and saved as fixtures as they are.
 */
object OcrShareName {

    private const val NO_STORE = "receipt"

    /**
     * @param storeName the recognised store, or null/blank to use "receipt"
     * @param date the receipt's date, or null to use [today]
     */
    fun of(storeName: String?, date: ReceiptDate?, today: ReceiptDate): String {
        val store = storeName?.let(::slug).orEmpty().ifEmpty { NO_STORE }
        val day = date ?: today
        return "%s-%04d-%02d-%02d-ocr".format(Locale.ROOT, store, day.year, day.month, day.day)
    }

    /** Lower case, no accents, anything else as single dashes: `"Crai Più!"` becomes `crai-piu`. */
    private fun slug(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}
