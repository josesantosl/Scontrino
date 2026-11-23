package com.pepesantos.scontrino.ocr.parse

/**
 * The Spanish company tax id (CIF, now "NIF de persona jurídica") printed alone on a
 * line, without label, as Mercadona does under its name: "A-46103834". Labelled ones
 * ("CIF: A46103834") are read by the parser's tax id list.
 */
object SpanishCif {

    /** Organisation letter, seven digits and the control character (digit or letter). */
    private val CIF = Regex("""^[ABCDEFGHJNPQRSUVW]\d{7}[0-9A-J]$""")

    private const val CONTROL_LETTERS = "JABCDEFGHI"

    /**
     * The CIF if [line] (upper case) holds nothing else, ignoring spaces, hyphens and
     * dots ("A- 46103834"); only with a valid control character, so that an unlabelled
     * code is not taken by chance.
     */
    fun findAlone(line: String): String? {
        val compact = line.filterNot { it.isWhitespace() || it == '-' || it == '.' }
        if (!CIF.matches(compact)) return null
        return compact.takeIf { isValid(it) }
    }

    /** Checks the control character of a CIF such as "A46103834". */
    fun isValid(cif: String): Boolean {
        if (!CIF.matches(cif)) return false
        // Odd positions are doubled and their digits added; even positions added as is.
        val sum = cif.substring(1, 8).withIndex().sumOf { (index, char) ->
            val digit = char - '0'
            if (index % 2 == 0) (digit * 2).let { it / 10 + it % 10 } else digit
        }
        val control = (10 - sum % 10) % 10
        val last = cif.last()
        return last == '0' + control || last == CONTROL_LETTERS[control]
    }
}
