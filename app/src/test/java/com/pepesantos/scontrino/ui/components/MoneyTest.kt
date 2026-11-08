package com.pepesantos.scontrino.ui.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MoneyTest {

    private val previous = Locale.getDefault()

    @Before
    fun setUp() = Locale.setDefault(Locale.US)

    @After
    fun tearDown() = Locale.setDefault(previous)

    @Test
    fun `presets use their symbols`() {
        assertEquals("€12.50", Money.format(12.5, "EUR"))
        assertEquals("$3.00", Money.format(3.0, "USD"))
        assertEquals("CHF 4.20", Money.format(4.2, "CHF"))
    }

    @Test
    fun `custom symbols are shown as typed`() {
        assertEquals("£1.00", Money.format(1.0, "£"))
        assertEquals("zł 1.00", Money.format(1.0, "zł"))
    }

    @Test
    fun `decimal separator follows the locale`() {
        Locale.setDefault(Locale.ITALY)
        assertEquals("€12,50", Money.format(12.5, "EUR"))
    }
}
