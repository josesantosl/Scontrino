package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryMatcherTest {

    @Test
    fun `detectCategoryLabel matches keywords across languages`() {
        assertEquals("plant_protein", CategoryMatcher.detectCategoryLabel("Tofu ahumado"))
        assertEquals("animal_protein", CategoryMatcher.detectCategoryLabel("Pechuga de pollo"))
        assertEquals("animal_protein", CategoryMatcher.detectCategoryLabel("Fresh salmon fillet"))
        assertEquals("vegetables_fruit", CategoryMatcher.detectCategoryLabel("Manzana fuji"))
        assertEquals("beverages", CategoryMatcher.detectCategoryLabel("Café solo"))
        assertNull(CategoryMatcher.detectCategoryLabel("Item Desconocido XYZ"))
    }

    @Test
    fun `getCategoryStringRes returns correct resource IDs`() {
        assertEquals(R.string.category_plant_protein, CategoryMatcher.getCategoryStringRes("plant_protein"))
        assertEquals(R.string.category_animal_protein, CategoryMatcher.getCategoryStringRes("animal_protein"))
        assertEquals(R.string.category_vegetables_fruit, CategoryMatcher.getCategoryStringRes("vegetables_fruit"))
        assertEquals(R.string.category_beverages, CategoryMatcher.getCategoryStringRes("beverages"))
        assertEquals(R.string.category_other, CategoryMatcher.getCategoryStringRes("other"))
        assertNull(CategoryMatcher.getCategoryStringRes("custom_category"))
    }
}
