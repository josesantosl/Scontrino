package com.pepesantos.scontrino.ocr.draft

import java.util.Locale

/**
 * How a name read on a receipt is compared with the user's products: case and
 * repeated spaces ignored. Used both to reuse an existing product in the draft and
 * to prefer the OCR reading whose names the user already knows.
 */
object ProductNames {
    fun normalize(name: String): String =
        name.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
