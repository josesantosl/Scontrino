package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.R
import com.pepesantos.scontrino.ui.theme.CategoryIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryMatcherTest {

    private fun assertCategory(expected: String, name: String) =
        assertEquals(name, expected, CategoryMatcher.detectCategoryLabel(name))

    @Test
    fun `detectCategoryLabel matches keywords across languages`() {
        assertCategory("legumes", "Tofu ahumado")
        assertCategory("meat", "Pechuga de pollo")
        assertCategory("seafood", "Fresh salmon fillet")
        assertCategory("vegetables_fruit", "Manzana fuji")
        assertCategory("beverages", "Café solo")
        assertNull(CategoryMatcher.detectCategoryLabel("Item Desconocido XYZ"))
    }

    @Test
    fun `typical italian receipt lines`() {
        assertCategory("dairy", "MOZZ. FIOR DI LATTE")
        assertCategory("meat", "PROSC.COTTO")
        assertCategory("bakery", "PAN BAULETTO")
        assertCategory("pasta_rice", "SPAGHETTI N.5")
        assertCategory("eggs", "UOVA FRESCHE")
        assertCategory("alcohol", "BIRRA MORETTI")
        assertCategory("ready_meals", "LASAGNE ALLA BOLOGNESE")
    }

    @Test
    fun `used-for wins over made-of`() {
        assertCategory("dairy", "Latte di soia")
        assertCategory("dairy", "Bebida de soja")
        assertCategory("condiments", "Salsa di soia")
        assertCategory("condiments", "Pesto alla genovese")
        assertCategory("condiments", "Ragù di carne")
        assertCategory("seafood", "Tonno all'olio")
        assertCategory("condiments", "Aceto di vino")
        assertCategory("pasta_rice", "Pasta all'uovo")
        assertCategory("personal_hygiene", "Pasta de dientes")
        assertCategory("beverages", "Succo di mela")
        assertCategory("vegetables_fruit", "Watermelon")
    }

    @Test
    fun `short words only match whole words`() {
        assertCategory("seafood", "Salmone affumicato") // not "sal"
        assertCategory("dairy", "Panna da cucina") // not "pan"
        assertCategory("snacks_sweets", "Gelato al limone") // not "gel"
        assertCategory("pets", "Gatto pollo") // cat food is pets, not meat
    }

    @Test
    fun `every matched label is a default category with a name`() {
        val labels = listOf(
            "Tofu", "Pollo", "Salmone", "Mela", "Latte", "Pane", "Riso", "Olio", "Biscotti", "Pizza",
            "Acqua", "Vino", "Detersivo", "Shampoo", "Pannolini", "Croccantini gatto", "Aspirina",
            "Biglietto treno", "Coperto", "Uova",
        ).map { CategoryMatcher.detectCategoryLabel(it) }
        labels.forEach { label ->
            assertNotNull(label)
            assert(label in CategoryIcons.defaults) { "$label is not a default category" }
        }
        CategoryIcons.defaults.keys.forEach { label ->
            assertNotNull(label, CategoryMatcher.getCategoryStringRes(label))
        }
    }

    @Test
    fun `getCategoryStringRes returns correct resource IDs`() {
        assertEquals(R.string.category_legumes, CategoryMatcher.getCategoryStringRes("legumes"))
        assertEquals(R.string.category_meat, CategoryMatcher.getCategoryStringRes("meat"))
        assertEquals(R.string.category_vegetables_fruit, CategoryMatcher.getCategoryStringRes("vegetables_fruit"))
        assertEquals(R.string.category_beverages, CategoryMatcher.getCategoryStringRes("beverages"))
        assertEquals(R.string.category_other, CategoryMatcher.getCategoryStringRes("other"))
        assertNull(CategoryMatcher.getCategoryStringRes("custom_category"))
    }
}
