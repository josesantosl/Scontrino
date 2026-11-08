package com.pepesantos.scontrino.ui.viewmodel

import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.ProductUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {

    private fun usage(id: Int, name: String, count: Int, alias: String? = null) =
        ProductUsage(Product(id = id, name = name, alias = alias), count)

    @Test
    fun `aliases list puts products without alias first, most bought first`() {
        val rows = listOf(
            usage(1, "VALSOIA 1LT", 9, alias = "latte di soia"),
            usage(2, "PANE", 2),
            usage(3, "BIBITA SOIA", 1, alias = "latte di soia"),
            usage(4, "MELE", 7),
            usage(5, "ACQUA", 2),
            usage(6, "PASTA BARILLA", 3, alias = "Pasta"),
        )

        val sorted = SettingsViewModel.sortForAliases(rows).map { it.product.id }

        // Without alias by frequency (ties by name), then aliased grouped by alias.
        assertEquals(listOf(4, 5, 2, 1, 3, 6), sorted)
    }
}
