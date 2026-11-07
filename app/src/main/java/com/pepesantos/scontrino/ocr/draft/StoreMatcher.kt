package com.pepesantos.scontrino.ocr.draft

import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.ocr.parse.editDistance
import java.text.Normalizer
import java.util.Locale

/**
 * Recognises which of the user's stores a receipt belongs to.
 *
 * 1. By name: a store whose name appears in the non-item text of the receipt
 *    (header, footer, unparsed rows). The user's own store names are the most
 *    reliable signal, so this wins.
 * 2. By VAT number, when no name is found (e.g. the logo is unreadable).
 */
object StoreMatcher {

    enum class Reason { NAME, VAT_NUMBER }

    data class Match(val store: Store, val reason: Reason)

    /** Names shorter than this are too ambiguous to look for ("A", "DM"...). */
    private const val MIN_NAME_LENGTH = 3

    /** From this length on, one misread character is tolerated ("BENNFT"). */
    private const val FUZZY_MIN_LENGTH = 5

    fun match(stores: List<Store>, lines: List<String>, vatNumber: String?): Match? {
        val lineTokens = lines.map(::tokens).filter { it.isNotEmpty() }

        val byName = stores
            .mapNotNull { store ->
                val nameTokens = tokens(store.name)
                if (nameTokens.joinToString("").length < MIN_NAME_LENGTH) return@mapNotNull null
                val occurrences = lineTokens.sumOf { line -> countOccurrences(line, nameTokens) }
                if (occurrences == 0) null else Triple(store, occurrences, nameTokens.joinToString(" ").length)
            }
            .sortedWith(
                compareByDescending<Triple<Store, Int, Int>> { it.second }
                    // "Carrefour Express" is more specific than "Carrefour".
                    .thenByDescending { it.third }
                    // Same rule as when saving: stores with a loyalty card first.
                    .thenByDescending { it.first.color != null }
            )
            .firstOrNull()
        if (byName != null) return Match(byName.first, Reason.NAME)

        if (vatNumber != null) {
            stores.firstOrNull { it.vatNumber == vatNumber }?.let { return Match(it, Reason.VAT_NUMBER) }
        }
        return null
    }

    private fun countOccurrences(line: List<String>, name: List<String>): Int =
        (0..line.size - name.size).count { start ->
            name.indices.all { i -> sameWord(line[start + i], name[i]) }
        }

    private fun sameWord(found: String, expected: String): Boolean =
        found == expected ||
            (expected.length >= FUZZY_MIN_LENGTH && editDistance(found, expected) <= 1)

    /** Upper case, no accents, punctuation as separators: `"bennet."` becomes `BENNET`. */
    private fun tokens(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.ROOT)
            .split(Regex("[^A-Z0-9]+"))
            .filter { it.isNotEmpty() }
}
