package com.pepesantos.scontrino.ocr.draft

import com.pepesantos.scontrino.data.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductNameMatcherTest {

    private fun product(id: Int, name: String) = Product(id = id, name = name, categoryId = 1)

    private fun match(read: String, vararg names: String): String? =
        ProductNameMatcher.Index(names.mapIndexed { i, n -> product(i + 1, n) }).match(read)?.name

    @Test
    fun `exact match ignores case and spaces`() {
        assertEquals("Soyadrink classico", match("SOYADRINK  CLASSICO", "Soyadrink classico"))
    }

    @Test
    fun `one misread letter is corrected`() {
        assertEquals("SOYADRINK CLASSICO", match("SOVADRINK CLASSICO", "SOYADRINK CLASSICO"))
    }

    @Test
    fun `two misread letters are corrected in a long name`() {
        assertEquals("MOZZARELLA FIOR DI LATTE", match("MOZZARELIA FIOR DI IATTE", "MOZZARELLA FIOR DI LATTE"))
    }

    @Test
    fun `a missing or extra letter counts as one`() {
        assertEquals("FALAFEL PICCANTE", match("FALAFEL PICANTE", "FALAFEL PICCANTE"))
        assertEquals("FALAFEL PICCANTE", match("FALAFEL PICCANTTE", "FALAFEL PICCANTE"))
    }

    @Test
    fun `three misread letters are not corrected`() {
        assertNull(match("MOZZARELIA FIOR DI IATTF", "MOZZARELLA FIOR DI LATTE"))
    }

    @Test
    fun `short names need to be closer`() {
        // 4 letters: only exact.
        assertNull(match("PERE", "PANE"))
        assertNull(match("PANF", "PANE"))
        // 5 to 9 letters: one letter.
        assertEquals("BANANE", match("BANAME", "BANANE"))
        assertNull(match("BALAME", "BANANE"))
    }

    @Test
    fun `different digits are a different product`() {
        assertNull(match("ACQUA NATURALE LT 2", "ACQUA NATURALE LT 1"))
        assertNull(match("PASSATA GR 250", "PASSATA GR 500"))
        assertEquals("ACQUA NATURALE LT 1", match("ACQUA NATURAIE LT 1", "ACQUA NATURALE LT 1"))
    }

    @Test
    fun `two equally close products give no correction`() {
        assertNull(match("LATTE PS INTERO", "LATTE PS INTERA", "LATTE PS INTERI"))
    }

    @Test
    fun `the closest product wins`() {
        assertEquals(
            "LATTE PS INTERO",
            match("LATTE PS INTERO", "LATTE PS INTERO", "LATTE PS INTERI"),
        )
        assertEquals(
            "YOGURT GRECO BIANCO",
            match("YOGURT GRECO BLANCO", "YOGURT GRECO BIANCO", "YOGURT GRECO BLANCO X"),
        )
    }
}
