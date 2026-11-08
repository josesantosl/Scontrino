package com.pepesantos.scontrino.ocr.draft

import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.PastPurchase
import com.pepesantos.scontrino.ui.viewmodel.PurchaseHistory
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.ocr.OcrFixtures
import com.pepesantos.scontrino.ocr.ReceiptRowBuilder
import com.pepesantos.scontrino.ocr.parse.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** From real OCR output to the data that prefills the new receipt screen. */
class ReceiptDraftBuilderTest {

    private val stores = listOf(
        Store(id = 1, name = "Lidl"),
        Store(id = 2, name = "Bennet", color = 0xFF00AA00),
    )

    private fun draft(fixture: String, products: List<Product> = emptyList()): ReceiptDraft {
        val rows = ReceiptRowBuilder.build(OcrFixtures.words(fixture), useEngineLines = true)
        return ReceiptDraftBuilder.build(ReceiptParser.parse(rows), stores, products, categories = emptyList())
    }

    private fun utcMidnight(year: Int, month: Int, day: Int) =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(year, month - 1, day) }.timeInMillis

    @Test
    fun `lidl - store, date and items with the corrected price`() {
        val draft = draft("lidl-torino-2026-08-17-lines")

        assertEquals("Lidl", draft.storeName)
        assertEquals(utcMidnight(2026, 8, 17), draft.dateMillis)
        assertEquals(9, draft.items.size)
        assertEquals(ItemEntry(name = "FALAFEL PICCANTE", price = 1.99, categoryId = 1), draft.items[3])
        assertEquals(16.67, draft.receiptTotal!!, 0.0)
        assertTrue(draft.addsUp)
    }

    @Test
    fun `bennet - discounts applied and repeated lines become a quantity`() {
        val draft = draft("bennet-torino-san-paolo-lines")

        assertEquals("Bennet", draft.storeName)
        assertEquals("07071700152", draft.storeVatNumber)
        // No date is printed on this photo's crop.
        assertNull(draft.dateMillis)

        // Two "CONT.LEGGEREZZA G" at 2,99 - 0,90 each: one entry, quantity 2.
        val cont = draft.items.single { it.name == "CONT.LEGGEREZZA G" }
        assertEquals(2.09, cont.price, 1e-9)
        assertEquals(2, cont.quantity)
        val natu = draft.items.single { it.name == "NATU' GATTO POLLO GR" }
        assertEquals(2, natu.quantity)

        // The items still add up to the total paid.
        assertEquals(17.68, draft.items.sumOf { it.price * it.quantity }, 1e-9)
    }

    @Test
    fun `an existing product gives its own name and category`() {
        val farinata = Product(id = 7, name = "Farinata", categoryId = 6)
        val draft = draft("bennet-torino-san-paolo-lines", products = listOf(farinata))

        val item = draft.items.single { it.name == "Farinata" }
        assertEquals(6, item.categoryId)
        assertEquals(2.35, item.price, 1e-9)
    }

    @Test
    fun `the store is recognised by VAT number when its name is not in the text`() {
        val renamed = listOf(Store(id = 2, name = "Il mio supermercato", vatNumber = "07071700152"))
        val rows = ReceiptRowBuilder.build(OcrFixtures.words("bennet-torino-san-paolo-2026-11-07"), useEngineLines = true)
        val draft = ReceiptDraftBuilder.build(ReceiptParser.parse(rows), renamed, emptyList(), emptyList())

        assertEquals("Il mio supermercato", draft.storeName)
        assertEquals(utcMidnight(2026, 11, 7), draft.dateMillis)
    }

    @Test
    fun `known weights are filled in for the same product at the same price`() {
        val draft = ReceiptDraft(
            storeName = "Bennet",
            storeVatNumber = null,
            dateMillis = null,
            items = listOf(
                ItemEntry(name = "LATTE PS INTERO", price = 1.29),
                ItemEntry(name = "BANANE", price = 1.87),
                ItemEntry(name = "PANE", price = 2.10, weight = 400.0),
            ),
            receiptTotal = null,
            addsUp = false,
        )
        val history = PurchaseHistory(
            listOf(
                PastPurchase("Latte PS intero", price = 1.29, weight = 1.0, date = 100L),
                PastPurchase("BANANE", price = 1.52, weight = 850.0, date = 100L),
                PastPurchase("PANE", price = 2.10, weight = 500.0, date = 100L),
            )
        )

        val items = ReceiptDraftBuilder.withKnownWeights(draft, history).items

        assertEquals(1.0, items[0].weight)
        assertNull(items[1].weight) // different price: no guess
        assertEquals(400.0, items[2].weight) // already known: kept
    }
}
