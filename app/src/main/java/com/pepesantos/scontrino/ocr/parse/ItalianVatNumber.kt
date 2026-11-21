package com.pepesantos.scontrino.ocr.parse

/**
 * The Italian store tax id, "P.IVA 07071700152" or "PI02275030233" (Lidl): eleven
 * digits whose last one is a check digit. The check digit lets a misread slashed
 * zero be fixed: "PI92275030233", "PIQ2275030233" and "PIG22 75030233" all are
 * 02275030233.
 */
internal object ItalianVatNumber {

    /** Letters that a slashed zero is read as. */
    private const val ZEROS = "OQGD@"

    private val LABELLED = Regex(
        """\bP\.?\s*I\.?\s*(?:V\.?\s*A\.?\s*)?:?\s*([\d$ZEROS](?:\s?[\d$ZEROS]){10})(?![\d$ZEROS])"""
    )

    /** The tax id in an upper-case [line], or null. */
    fun find(line: String): String? {
        val raw = LABELLED.find(line)?.groupValues?.get(1) ?: return null
        val digits = raw.filterNot { it.isWhitespace() }.map { if (it in ZEROS) '0' else it }.joinToString("")
        return candidates(digits).firstOrNull(::isValid) ?: digits
    }

    /** As read, then with one "9" taken for a zero, then all of them. */
    private fun candidates(digits: String): List<String> =
        listOf(digits) +
            digits.indices.filter { digits[it] == '9' }.map { digits.replaceRange(it, it + 1, "0") } +
            digits.replace('9', '0')

    /** Luhn-style check: odd positions as they are, even ones doubled (digits summed). */
    fun isValid(digits: String): Boolean {
        if (digits.length != 11 || !digits.all { it.isDigit() }) return false
        val sum = (0 until 10).sumOf { i ->
            val digit = digits[i] - '0'
            if (i % 2 == 0) digit else (digit * 2).let { if (it > 9) it - 9 else it }
        }
        return (10 - sum % 10) % 10 == digits[10] - '0'
    }
}
