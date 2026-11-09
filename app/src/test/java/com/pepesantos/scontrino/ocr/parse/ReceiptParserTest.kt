package com.pepesantos.scontrino.ocr.parse

import com.pepesantos.scontrino.ocr.BoundingBox
import com.pepesantos.scontrino.ocr.OcrWord
import com.pepesantos.scontrino.ocr.ReceiptRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parser rules on hand-written rows; each argument of [row] is a column. */
class ReceiptParserTest {

    /** "word@80" sets the OCR confidence of that word (default 95). */
    private fun row(vararg segments: String): ReceiptRow {
        val segmentWords = segments.map { segment ->
            segment.split(" ").filter { it.isNotBlank() }.map { token ->
                val (text, confidence) = token.split("@").let { it[0] to (it.getOrNull(1)?.toFloat() ?: 95f) }
                OcrWord(text, BoundingBox(0, 0, 10, 10), confidence)
            }
        }
        return ReceiptRow(words = segmentWords.flatten(), segmentWords = segmentWords)
    }

    @Test
    fun `items between the column header and the total`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("SUPERMERCATO PROVA"),
                row("DESCRIZIONE", "IVA PREZZO"),
                row("PANE", "4%", "1,20"),
                row("LATTE", "10%", "0,99"),
                row("TOTALE", "2,19"),
            )
        )
        assertEquals(listOf("PANE", "LATTE"), parsed.items.map { it.name })
        assertEquals(listOf(120L, 99L), parsed.items.map { it.priceCents })
        assertEquals(listOf(4.0, 10.0), parsed.items.map { it.vatRate })
        assertEquals(219L, parsed.totalCents)
        assertTrue(parsed.isConsistent)
        assertEquals(listOf("SUPERMERCATO PROVA"), parsed.headerLines)
    }

    @Test
    fun `discount lines reduce the previous item`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("DESCRIZIONE", "PREZZO"),
                row("353406 CONT.LEGGEREZZA G", "2,99 B"),
                row("Sconto Art. 30%", "-0,90"),
                row("TOTALE", "2,09"),
            )
        )
        val item = parsed.items.single()
        assertEquals("CONT.LEGGEREZZA G", item.name)
        assertEquals("353406", item.code)
        assertEquals("B", item.vatCode)
        assertEquals(90L, item.discountCents)
        assertTrue(parsed.isConsistent)
    }

    @Test
    fun `a Sconto line is a discount even if the minus was lost`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("ARTICOLO", "2,99"),
                row("Sconto Art. 30%", "0,90"),
                row("TOTALE", "2,09"),
            )
        )
        assertEquals(90L, parsed.items.single().discountCents)
    }

    @Test
    fun `VAT letters use the legend printed on the receipt`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("ARTICOLO", "2,99 C"),
                row("TOTALE", "2,99"),
                row("C: IVA 10,0%"),
            )
        )
        assertEquals(10.0, parsed.items.single().vatRate!!, 0.0)
    }

    @Test
    fun `a name whose price fell on the next row is joined`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("COLA"),
                row("22%", "0,55"),
                row("TOTALE", "0,55"),
            )
        )
        assertEquals("COLA", parsed.items.single().name)
        assertEquals(55L, parsed.items.single().priceCents)
    }

    @Test
    fun `an unreadable total falls back to the payment lines`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("ARTICOLO", "17,68"),
                row("TOTALE COMPLESSIVO E", "1768"),
                row("DI CUI IVA", "2.08"),
                row("PAGAMENTO ELETTRONICO", "17,68"),
                row("RESTO", "0,00"),
            )
        )
        assertEquals(1768L, parsed.totalCents)
    }

    @Test
    fun `a single misread price is corrected using the total`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("FALAFEL AGLIO", "1,99@96"),
                row("FALAFEL PICCANTE", "11,99@14"),
                row("TOTALE", "3,98"),
            )
        )
        val corrected = parsed.items[1]
        assertEquals(199L, corrected.priceCents)
        assertEquals(1199L, corrected.correctedFromCents)
        assertNull(parsed.items[0].correctedFromCents)
        assertTrue(parsed.isConsistent)
    }

    @Test
    fun `among several possible corrections the least confident read wins`() {
        // 4 cents too many: 2,99 → 2,95 or 2,39 → 2,35 are both one digit away.
        val parsed = ReceiptParser.parse(
            listOf(
                row("ACQUA", "2,99@92"),
                row("FARINATA", "2,39@76"),
                row("TOTALE", "5,34"),
            )
        )
        assertEquals(listOf(299L, 235L), parsed.items.map { it.priceCents })
    }

    @Test
    fun `no correction when no price is one digit away`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("ARTICOLO", "2,99"),
                row("TOTALE", "7,50"),
            )
        )
        assertEquals(299L, parsed.items.single().priceCents)
        assertFalse(parsed.isConsistent)
    }

    @Test
    fun `ecuadorian layout - DESCR header, dot decimals, CAMBIO is not the total`() {
        // Names and numbers are made up; the layout is that of a real receipt.
        val parsed = ReceiptParser.parse(
            listOf(
                row("CORPORACION EJEMPLO S.A."),
                row("RUC:0990000000001"),
                row("CODIGO DESCR. CANT. P.UNIT.", "VALOR"),
                row("008336 HIELO 1", "0.99", "0.99"),
                row("001242 TILAPIA 1", "5.70", "5.70"),
                row("SUBTOTAL", "6.69"),
                row("TOTAL", "6.69"),
                row("CAMBIO", "3.31"),
                row("2026/11/08 TIENDA:5 POS:202"),
                row("NOMBRE : CLIENTE DE PRUEBA"),
                row("RUC/CED/PAS: 0900000000"),
                row("RUC Proveedor: 0990000000002"),
            )
        )
        assertEquals(listOf("008336", "001242"), parsed.items.map { it.code })
        assertEquals(listOf(99L, 570L), parsed.items.map { it.priceCents })
        assertEquals(669L, parsed.totalCents)
        assertTrue(parsed.isConsistent)
        assertEquals(ReceiptDate(2026, 11, 8), parsed.date)
        // The store's RUC, not the customer's id nor the e-invoicing provider's.
        assertEquals("0990000000001", parsed.vatNumber)
    }

    @Test
    fun `a product containing DESCR is not the column header`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("PANE", "1,20"),
                row("LECHE DESCREMADA", "0,99"),
                row("TOTALE", "2,19"),
            )
        )
        assertEquals(listOf("PANE", "LECHE DESCREMADA"), parsed.items.map { it.name })
    }

    @Test
    fun `noise rows in the item list are reported, not turned into items`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("DESCRIZIONE", "PREZZO"),
                row(". |", "00"),
                row("ARTICOLO", "1,00"),
                row("TOTALE", "1,00"),
            )
        )
        assertEquals(listOf("ARTICOLO"), parsed.items.map { it.name })
        assertEquals(1, parsed.unparsedRows.size)
    }
}
