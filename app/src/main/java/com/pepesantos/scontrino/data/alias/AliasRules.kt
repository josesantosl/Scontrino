package com.pepesantos.scontrino.data.alias

import com.pepesantos.scontrino.data.model.Product
import java.util.Locale

/** Something the user can pick as alias: an alias already in use, or a product without one. */
data class AliasSuggestion(val text: String, val isExistingAlias: Boolean)

/** What [AliasRules.resolve] decided for an alias typed or picked by the user. */
data class AliasResolution(
    /** Alias to store, or null to remove it. */
    val alias: String?,
    /**
     * Product without alias whose name was picked as the alias: it becomes the alias of
     * itself too, so that it is grouped (and shown as grouped) with the others.
     */
    val promotedProductId: Int?,
)

/**
 * Rules of the product aliases (n products -> 1 alias). Aliases are plain names and never
 * point to another alias: picking a product that already has an alias means "the same as
 * that product", so its alias is used instead, and no chains can appear.
 */
object AliasRules {

    /** Trimmed, with single spaces, or null if blank. */
    fun normalize(alias: String?): String? =
        alias?.trim()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }

    /** Key of the group of a product: what statistics and price comparison show. */
    fun groupName(product: Product): String = product.alias ?: product.name

    /**
     * Category of the group called [groupName]: the most used one among its products,
     * ignoring "other" ([otherCategoryId]); null if none of them has a real category.
     */
    fun groupCategoryId(groupName: String, products: List<Product>, otherCategoryId: Int = 1): Int? =
        products
            .filter { groupName(it).equals(groupName, ignoreCase = true) && it.categoryId != otherCategoryId }
            .groupingBy { it.categoryId }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    /**
     * Decides the alias to store for product [selfId] (null if it is not saved yet) from
     * what the user typed, reusing the spelling of an existing alias or product name.
     */
    fun resolve(input: String?, products: List<Product>, selfId: Int?): AliasResolution {
        val typed = normalize(input) ?: return AliasResolution(null, null)

        products.firstNotNullOfOrNull { product ->
            product.alias?.takeIf { it.equals(typed, ignoreCase = true) }
        }?.let { return AliasResolution(it, null) }

        val named = products.firstOrNull { it.name.equals(typed, ignoreCase = true) }
            ?: return AliasResolution(typed, null)
        if (named.id == selfId) return AliasResolution(named.name, null)
        // "Same as a product that already has an alias" -> that alias, never a chain.
        named.alias?.let { return AliasResolution(it, null) }
        return AliasResolution(named.name, promotedProductId = named.id)
    }

    /**
     * Aliases in use and names of products without alias, for the product called
     * [productName]. With an empty [query], the ones sharing words with the product name
     * come first (e.g. "BIBITA SOIA 1L" suggests "latte di soia"); otherwise only the ones
     * containing every word typed.
     */
    fun suggestions(
        query: String,
        productName: String,
        products: List<Product>,
        limit: Int = 8,
    ): List<AliasSuggestion> {
        val aliases = products.mapNotNull { it.alias }.distinctBy { it.lowercase() }
        val aliasKeys = aliases.map { it.lowercase() }.toSet()
        val plainNames = products
            .filter { it.alias == null }
            .map { it.name }
            .filter { !it.equals(productName, ignoreCase = true) && it.lowercase() !in aliasKeys }
            .distinctBy { it.lowercase() }
        val candidates = aliases.map { AliasSuggestion(it, isExistingAlias = true) } +
            plainNames.map { AliasSuggestion(it, isExistingAlias = false) }

        val queryWords = normalize(query)?.lowercase()?.split(" ").orEmpty()
        if (queryWords.isEmpty()) {
            val productWords = words(productName)
            return candidates
                .map { it to sharedWords(productWords, words(it.text)) }
                .filter { (_, shared) -> shared > 0 }
                .sortedWith(
                    compareByDescending<Pair<AliasSuggestion, Int>> { it.second }
                        .thenByDescending { it.first.isExistingAlias }
                        .thenBy { it.first.text.lowercase() }
                )
                .map { it.first }
                .take(limit)
        }

        val firstWord = queryWords.first()
        return candidates
            .filter { candidate -> queryWords.all { candidate.text.contains(it, ignoreCase = true) } }
            .sortedWith(
                compareByDescending<AliasSuggestion> { it.isExistingAlias }
                    .thenByDescending { it.text.startsWith(firstWord, ignoreCase = true) }
                    .thenBy { it.text.lowercase() }
            )
            .take(limit)
    }

    /** Meaningful words: no sizes or codes ("1LT", "500G"), no short ones ("di", "x"). */
    private fun words(text: String): Set<String> =
        text.lowercase(Locale.ROOT)
            .split(NON_WORD)
            .filter { it.length >= 3 && it.none(Char::isDigit) }
            .toSet()

    /** Words in common, also counting prefixes of 4+ letters ("bisc" / "biscotti"). */
    private fun sharedWords(a: Set<String>, b: Set<String>): Int =
        a.count { wa ->
            b.any { wb -> wa == wb || (minOf(wa.length, wb.length) >= 4 && (wa.startsWith(wb) || wb.startsWith(wa))) }
        }

    private val WHITESPACE = Regex("\\s+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
}
