package com.pepesantos.scontrino.ocr.draft

import com.pepesantos.scontrino.data.model.Product

/**
 * Finds the user's product behind a name read on a receipt, forgiving up to two
 * misread letters ("SOVADRINK" → "SOYADRINK"), so a misreading doesn't create a new
 * product that breaks aliases, price comparison and stats.
 *
 * Kept on the safe side, since a wrong match is worse than none:
 * - short names allow fewer edits (one per five characters, at most two): "PANE" and
 *   "PERE" are two letters apart but different products;
 * - digits must be the same ("LT 1" vs "LT 2", "GR 500" vs "GR 250" are other sizes);
 * - when two products are equally close, no correction is made.
 */
object ProductNameMatcher {

    private const val MAX_EDITS = 2
    private const val CHARS_PER_EDIT = 5

    /** [products] prepared once for matching every line of a receipt. */
    class Index(products: List<Product>) {
        private val byName = products.associateBy { ProductNames.normalize(it.name) }

        fun match(name: String): Product? {
            val read = ProductNames.normalize(name)
            byName[read]?.let { return it }

            val allowed = minOf(MAX_EDITS, read.length / CHARS_PER_EDIT)
            if (allowed == 0) return null
            val readDigits = digits(read)

            var best: Product? = null
            var bestDistance = Int.MAX_VALUE
            var tied = false
            for ((known, product) in byName) {
                if (digits(known) != readDigits) continue
                val distance = editDistance(read, known, allowed)
                if (distance > allowed) continue
                when {
                    distance < bestDistance -> {
                        best = product
                        bestDistance = distance
                        tied = false
                    }
                    distance == bestDistance -> tied = true
                }
            }
            return if (tied) null else best
        }
    }

    private fun digits(name: String): String = name.filter { it.isDigit() }

    /**
     * Levenshtein distance between [a] and [b] (a letter changed, added or missing
     * counts as one), or [limit] + 1 as soon as it is known to exceed [limit].
     */
    internal fun editDistance(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
