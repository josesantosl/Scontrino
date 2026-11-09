package com.pepesantos.scontrino.ocr.parse

/** Finds the purchase date (and time, if printed next to it) in the receipt text. */
object ReceiptDateParser {

    // "@" is included because the OCR sometimes reads a slashed zero that way.
    /** Day first: "17-08-2026", "07/11/2026", "05.03.26" (Italy, Spain...). */
    private val DAY_FIRST = Regex("""(?<![\d@])([\d@]{1,2})[-/.]([\d@]{1,2})[-/.]([\d@]{4}|[\d@]{2})(?![\d@])""")

    /** Year first: "2026/11/08" (Ecuador, ISO). Four-digit year only, to avoid ambiguity. */
    private val YEAR_FIRST = Regex("""(?<![\d@])([\d@]{4})[-/.]([\d@]{1,2})[-/.]([\d@]{1,2})(?![\d@])""")

    private val TIME = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")

    fun find(lines: List<String>): ReceiptDate? {
        for (line in lines) {
            val candidates = YEAR_FIRST.findAll(line).map { it to true } +
                DAY_FIRST.findAll(line).map { it to false }
            for ((match, yearFirst) in candidates) {
                val (first, second, third) = match.destructured
                val (rawDay, rawMonth, rawYear) = if (yearFirst) Triple(third, second, first) else Triple(first, second, third)

                val day = readNumber(rawDay, 1..31) ?: continue
                val month = readNumber(rawMonth, 1..12) ?: continue
                val year = readNumber(rawYear, 0..9999)
                    ?.let { if (rawYear.length == 2) 2000 + it else it }
                    ?.takeIf { it in 2000..2100 }
                    ?: continue

                val time = TIME.find(line, match.range.last + 1)
                val hour = time?.groupValues?.get(1)?.toInt()?.takeIf { it in 0..23 }
                val minute = time?.groupValues?.get(2)?.toInt()?.takeIf { it in 0..59 }
                return if (hour != null && minute != null) {
                    ReceiptDate(year, month, day, hour, minute)
                } else {
                    ReceiptDate(year, month, day)
                }
            }
        }
        return null
    }

    /**
     * Many receipt printers use a slashed zero, which the OCR reads as "9" or "@":
     * "17-98-2026" is really "17-08-2026". When the plain number is out of range,
     * retry with those characters as zeros.
     */
    private fun readNumber(raw: String, range: IntRange): Int? {
        val plain = raw.replace('@', '0')
        plain.toIntOrNull()?.takeIf { it in range }?.let { return it }
        return plain.replace('9', '0').toIntOrNull()?.takeIf { it in range }
    }
}
