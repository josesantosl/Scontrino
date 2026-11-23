package com.pepesantos.scontrino.ocr.draft

import com.pepesantos.scontrino.data.model.ItemEntry
import com.pepesantos.scontrino.data.model.PastPurchase
import com.pepesantos.scontrino.ui.viewmodel.PurchaseHistory
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.Store
import com.pepesantos.scontrino.ocr.OcrFixtures
import com.pepesantos.scontrino.ocr.ReceiptRowBuilder
import com.pepesantos.scontrino.ocr.parse.ParsedItem
import com.pepesantos.scontrino.ocr.parse.ParsedReceipt
import com.pepesantos.scontrino.ocr.parse.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        // The price as read is kept, to show the correction while reviewing; nothing else changed.
        assertEquals(draft.items.size, draft.itemCorrections.size)
        val corrected = draft.itemCorrections.withIndex().filter { it.value != ItemCorrection.NONE }
        assertEquals(listOf(3), corrected.map { it.index })
        assertNotEquals(199L, corrected.single().value.readPriceCents)
        assertNull(corrected.single().value.readName)
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

        // Each entry keeps the rows of every line grouped into it (item + discount, twice);
        // printed one after the other, they show as a single crop.
        assertEquals(draft.items.size, draft.itemRows.size)
        val contRows = draft.itemRows[draft.items.indexOf(cont)]
        assertEquals(4, contRows.size)
        assertEquals(1, ReceiptLineCrops.runs(contRows).size)

        // The items still add up to the total paid.
        assertEquals(17.68, draft.items.sumOf { it.price * it.quantity }, 1e-9)
    }

    @Test
    fun `carrefour - a printed quantity line becomes unit price and quantity`() {
        val draft = draft("carrefour-torino-2026-11-09")

        // "2 x 0,79" then "FARINA\"00\" CRF KG1  1,58".
        val flour = draft.items.single { it.name.startsWith("FARINA") }
        assertEquals(0.79, flour.price, 1e-9)
        assertEquals(2, flour.quantity)

        assertEquals(3, draft.items.size)
        assertEquals(8.06, draft.items.sumOf { it.price * it.quantity }, 1e-9)
        assertTrue(draft.addsUp)
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
    fun `a misread name takes the existing product and groups with its correct reading`() {
        val soya = Product(id = 3, name = "SOYADRINK CLASSICO", categoryId = 4)
        fun line(name: String) = ParsedItem(name = name, code = null, priceCents = 189, priceConfidence = 90f, rowIndex = 0)
        val parsed = ParsedReceipt(
            items = listOf(line("SOVADRINK CLASSICO"), line("SOYADRINK CLASSICO"), line("SOVADRLNK CLASSLCO")),
            totalCents = 567,
            date = null,
            vatNumber = null,
            headerLines = emptyList(),
            unparsedRows = emptyList(),
        )

        val draft = ReceiptDraftBuilder.build(parsed, stores, listOf(soya), categories = emptyList())
        val items = draft.items

        // The first two lines are one product bought twice; the third is three letters off.
        assertEquals(2, items.size)
        assertEquals(ItemEntry(name = "SOYADRINK CLASSICO", price = 1.89, quantity = 2, categoryId = 4), items[0])
        assertEquals("SOVADRLNK CLASSLCO", items[1].name)
        // The misread is shown even though the other line was read right.
        assertEquals(
            listOf(ItemCorrection(readName = "SOVADRINK CLASSICO"), ItemCorrection.NONE),
            draft.itemCorrections,
        )
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
