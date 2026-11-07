package com.pepesantos.scontrino.ocr.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountsAndDatesTest {

    @Test
    fun `amounts are read in cents`() {
        assertEquals(199L, Amounts.parseCents("1,99"))
        assertEquals(145L, Amounts.parseCents("1.45"))
        assertEquals(1667L, Amounts.parseCents("16,67"))
        assertEquals(-90L, Amounts.parseCents("-0,90"))
    }

    @Test
    fun `stray OCR marks around amounts are ignored`() {
        assertEquals(1667L, Amounts.parseCents("‘16,67"))
        assertEquals(199L, Amounts.parseCents("1,99."))
        assertEquals(55L, Amounts.parseCents("|0,55"))
    }

    @Test
    fun `a tilde is a misread minus`() {
        assertEquals(-90L, Amounts.parseCents("~0,90"))
    }

    @Test
    fun `things that are not amounts are rejected`() {
        assertNull(Amounts.parseCents("1768"))      // total with the comma lost
        assertNull(Amounts.parseCents("19,9"))      // one decimal
        assertNull(Amounts.parseCents("22%"))
        assertNull(Amounts.parseCents("17:24"))
        assertNull(Amounts.parseCents("0755-9021"))
        assertNull(Amounts.parseCents("ARANCIA"))
    }

    @Test
    fun `VAT rates are read with surrounding marks`() {
        assertEquals(22.0, Amounts.parsePercent("22%")!!, 0.0)
        assertEquals(22.0, Amounts.parsePercent("/22%")!!, 0.0)
        assertEquals(10.0, Amounts.parsePercent("10%.")!!, 0.0)
        assertEquals(4.0, Amounts.parsePercent("4,0%")!!, 0.0)
        assertNull(Amounts.parsePercent("1,99"))
    }

    @Test
    fun `amounts are formatted with a comma`() {
        assertEquals("1,99", Amounts.format(199))
        assertEquals("0,05", Amounts.format(5))
        assertEquals("-0,90", Amounts.format(-90))
    }

    @Test
    fun `dates with time in common formats`() {
        assertEquals(ReceiptDate(2026, 8, 17, 17, 24), ReceiptDateParser.find(listOf("17-08-2026 17:24")))
        assertEquals(
            ReceiptDate(2026, 11, 7, 9, 40),
            ReceiptDateParser.find(listOf("RESTO 0,00", "07/11/2026 09:40  DOC. N0106-0021"))
        )
        assertEquals(ReceiptDate(2026, 3, 5), ReceiptDateParser.find(listOf("05.03.26")))
    }

    @Test
    fun `slashed zeros read as 9 or at sign are fixed when the date is impossible`() {
        assertEquals(ReceiptDate(2026, 8, 17, 17, 24), ReceiptDateParser.find(listOf("17-98-2026 17:24")))
        assertEquals(ReceiptDate(2026, 8, 17, 17, 24), ReceiptDateParser.find(listOf("17-@8-2026 17:24")))
    }

    @Test
    fun `numbers that only look like dates are ignored`() {
        assertNull(ReceiptDateParser.find(listOf("DOCUMENTO N. 0755-9021", "tel:+39 800 919024")))
        assertNull(ReceiptDateParser.find(listOf("45-13-2026")))
    }
}
