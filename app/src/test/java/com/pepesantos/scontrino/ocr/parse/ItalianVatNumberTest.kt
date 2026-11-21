package com.pepesantos.scontrino.ocr.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItalianVatNumberTest {

    @Test
    fun `check digit`() {
        assertTrue(ItalianVatNumber.isValid("02275030233")) // Lidl
        assertTrue(ItalianVatNumber.isValid("07071700152")) // Bennet
        assertTrue(ItalianVatNumber.isValid("02924950963")) // Carrefour
        assertFalse(ItalianVatNumber.isValid("92275030233"))
    }

    @Test
    fun `labels and slashed zero misreads`() {
        assertEquals("07071700152", ItalianVatNumber.find("P.IVA 07071700152"))
        assertEquals("02924950963", ItalianVatNumber.find("P. IVA: 02924950963"))
        assertEquals("02275030233", ItalianVatNumber.find("PI02275030233 RAEE IT08020000001777"))
        assertEquals("02275030233", ItalianVatNumber.find("PI92275030233 RAEE"))
        assertEquals("02275030233", ItalianVatNumber.find("PIQ2275030233 RAEE"))
        assertEquals("02275030233", ItalianVatNumber.find("PIG22 75030233 RAEE IT08920000001777"))
    }

    @Test
    fun `words starting with PI are not tax ids`() {
        assertNull(ItalianVatNumber.find("FALAFEL PICCANTE 1,99"))
        assertNull(ItalianVatNumber.find("PIZZA POMODORISSIM 1,84"))
    }
}
