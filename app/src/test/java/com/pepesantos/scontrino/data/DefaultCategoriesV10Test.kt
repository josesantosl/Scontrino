package com.pepesantos.scontrino.data

import com.pepesantos.scontrino.ui.theme.CategoryIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoriesV10Test {

    @Test
    fun `renamed labels are current defaults`() {
        DefaultCategoriesV10.renamed.values.forEach { assertTrue(it, it in CategoryIcons.defaults) }
        assertTrue(DefaultCategoriesV10.REMOVED !in CategoryIcons.defaults)
    }

    @Test
    fun `split categories move only to their new parts`() {
        assertEquals("seafood", DefaultCategoriesV10.newLabel("meat", "Salmone"))
        assertEquals("eggs", DefaultCategoriesV10.newLabel("meat", "Uova"))
        assertNull(DefaultCategoriesV10.newLabel("meat", "Pollo"))
        assertEquals("bakery", DefaultCategoriesV10.newLabel("pasta_rice", "Pane integrale"))
        assertNull(DefaultCategoriesV10.newLabel("pasta_rice", "Spaghetti"))
        assertEquals("alcohol", DefaultCategoriesV10.newLabel("beverages", "Birra"))
    }

    @Test
    fun `manual choices outside the splits are kept`() {
        // A user put biscuits under dairy on purpose: not a split, stays.
        assertNull(DefaultCategoriesV10.newLabel("dairy", "Biscotti"))
        assertNull(DefaultCategoriesV10.newLabel("condiments", "Tonno all'olio"))
    }

    @Test
    fun `removed canned category goes by name or to condiments`() {
        assertEquals("seafood", DefaultCategoriesV10.newLabel("canned_sauces", "Tonno all'olio"))
        assertEquals("legumes", DefaultCategoriesV10.newLabel("canned_sauces", "Ceci lessati"))
        assertEquals("condiments", DefaultCategoriesV10.newLabel("canned_sauces", "XYZ"))
    }

    @Test
    fun `uncategorized products get a category when the name matches`() {
        assertEquals("ready_meals", DefaultCategoriesV10.newLabel("other", "Pizza margherita"))
        assertNull(DefaultCategoriesV10.newLabel("other", "XYZ"))
    }
}
