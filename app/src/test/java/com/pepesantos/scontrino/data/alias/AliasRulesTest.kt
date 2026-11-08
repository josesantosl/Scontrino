package com.pepesantos.scontrino.data.alias

import com.pepesantos.scontrino.data.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasRulesTest {

    private val valsoia = Product(id = 1, name = "VALSOIA 1LT", alias = "latte di soia")
    private val vemondo = Product(id = 2, name = "VEMONDO BIBITA SOIA 1LT")
    private val biscotti = Product(id = 3, name = "Biscotti integrali")
    private val pane = Product(id = 4, name = "PANE COMUNE", alias = "Pane")
    private val products = listOf(valsoia, vemondo, biscotti, pane)

    @Test
    fun `blank alias removes it`() {
        assertEquals(AliasResolution(null, null), AliasRules.resolve("   ", products, selfId = 2))
        assertEquals(AliasResolution(null, null), AliasRules.resolve(null, products, selfId = 2))
    }

    @Test
    fun `new alias is trimmed with single spaces`() {
        assertEquals(
            AliasResolution("latte  di avena".replace("  ", " "), null),
            AliasRules.resolve("  latte   di avena ", products, selfId = 2)
        )
    }

    @Test
    fun `existing alias keeps its spelling`() {
        assertEquals(AliasResolution("latte di soia", null), AliasRules.resolve("LATTE DI SOIA", products, selfId = 2))
    }

    @Test
    fun `picking a product without alias makes it the alias of both`() {
        val resolution = AliasRules.resolve("biscotti INTEGRALI", products, selfId = 2)
        assertEquals("Biscotti integrali", resolution.alias)
        assertEquals(3, resolution.promotedProductId)
    }

    @Test
    fun `picking a product that has an alias uses its alias, never a chain`() {
        assertEquals(AliasResolution("latte di soia", null), AliasRules.resolve("VALSOIA 1LT", products, selfId = 2))
    }

    @Test
    fun `own name as alias promotes nothing`() {
        assertEquals(
            AliasResolution("VEMONDO BIBITA SOIA 1LT", null),
            AliasRules.resolve("vemondo bibita soia 1lt", products, selfId = 2)
        )
    }

    @Test
    fun `unsaved product picking another product promotes it`() {
        val resolution = AliasRules.resolve("Biscotti integrali", products, selfId = null)
        assertEquals(3, resolution.promotedProductId)
    }

    @Test
    fun `without query suggests aliases and products sharing words with the name`() {
        val suggestions = AliasRules.suggestions("", "BIBITA SOIA BIO 1L", products)
        assertEquals(
            listOf(
                AliasSuggestion("latte di soia", isExistingAlias = true),
                AliasSuggestion("VEMONDO BIBITA SOIA 1LT", isExistingAlias = false),
            ).toSet(),
            suggestions.toSet()
        )
        // Sharing two words beats sharing one.
        assertEquals("VEMONDO BIBITA SOIA 1LT", suggestions.first().text)
    }

    @Test
    fun `aliased products are never suggested, their alias is`() {
        val texts = AliasRules.suggestions("a", "Something", products).map { it.text }
        assertFalse("VALSOIA 1LT" in texts)
        assertFalse("PANE COMUNE" in texts)
        assertTrue("latte di soia" in texts)
        assertTrue("Pane" in texts)
    }

    @Test
    fun `the product itself is not suggested`() {
        val texts = AliasRules.suggestions("soia", "VEMONDO BIBITA SOIA 1LT", products).map { it.text }
        assertEquals(listOf("latte di soia"), texts)
    }

    @Test
    fun `query matches every word, aliases first`() {
        val suggestions = AliasRules.suggestions("soia 1lt", "x", products + Product(id = 9, name = "TOFU"))
        assertEquals(listOf("VEMONDO BIBITA SOIA 1LT"), suggestions.map { it.text })
        val withAlias = AliasRules.suggestions("i", "x", products)
        assertTrue(withAlias.first().isExistingAlias)
    }

    @Test
    fun `group category is the most used real category of the group`() {
        val group = listOf(
            Product(id = 1, name = "VALSOIA 1LT", categoryId = 5, alias = "latte di soia"),
            Product(id = 2, name = "BIBITA SOIA", categoryId = 5, alias = "latte di soia"),
            Product(id = 3, name = "SOIA DRINK", categoryId = 8, alias = "latte di soia"),
            Product(id = 4, name = "SOIA X", categoryId = 1, alias = "latte di soia"),
            Product(id = 5, name = "TOFU", categoryId = 3),
        )
        assertEquals(5, AliasRules.groupCategoryId("LATTE DI SOIA", group))
    }

    @Test
    fun `group category counts the product named like the alias and ignores other`() {
        val products = listOf(
            Product(id = 1, name = "Biscotti integrali", categoryId = 6),
            Product(id = 2, name = "BISC. INT.", categoryId = 1, alias = "Biscotti integrali"),
        )
        assertEquals(6, AliasRules.groupCategoryId("Biscotti integrali", products))
        assertNull(AliasRules.groupCategoryId("Biscotti integrali", products.drop(1)))
        assertNull(AliasRules.groupCategoryId("nuovo alias", products))
    }

    @Test
    fun `normalize collapses spaces and drops blanks`() {
        assertEquals("a b", AliasRules.normalize("  a \t b "))
        assertNull(AliasRules.normalize(" "))
    }
}
