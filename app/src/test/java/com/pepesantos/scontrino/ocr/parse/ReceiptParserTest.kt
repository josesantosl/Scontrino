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
    fun `US sales tax is added on top of the items, past a subtotal equal to them`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("WALMART"),
                row("MILK", "3.49"),
                row("BREAD", "2.51"),
                row("SUBTOTAL", "6.00"),
                row("TAX 1 8.000 %", "0.48"),
                row("TOTAL", "6.48"),
                row("VISA TEND", "6.48"),
            )
        )
        assertEquals(listOf("MILK", "BREAD"), parsed.items.map { it.name })
        assertEquals(48L, parsed.taxCents)
        assertEquals(648L, parsed.totalCents)
        assertTrue(parsed.isConsistent)
        assertTrue(parsed.items.all { it.correctedFromCents == null })
    }

    @Test
    fun `state and city taxes add up to the tax`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("COFFEE", "10.00"),
                row("SUBTOTAL", "10.00"),
                row("STATE TAX", "0.40"),
                row("CITY TAX", "0.20"),
                row("TOTAL", "10.60"),
            )
        )
        assertEquals(60L, parsed.taxCents)
        assertEquals(1060L, parsed.totalCents)
        assertTrue(parsed.isConsistent)
    }

    @Test
    fun `VAT included in the prices is not added`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("DESCRIZIONE", "PREZZO"),
                row("PANE", "1,20"),
                row("LATTE", "0,99"),
                row("TOTALE COMPLESSIVO", "2,19"),
                row("DI CUI IVA", "0,14"),
                row("PAGAMENTO CONTANTE", "2,19"),
            )
        )
        assertNull(parsed.taxCents)
        assertEquals(219L, parsed.totalCents)
        assertTrue(parsed.isConsistent)
    }

    @Test
    fun `Ecuadorian VAT breakdown is included in the prices, even if a line matches by chance`() {
        // Prices include IVA: SUBTOTAL 15% is the base, IVA 15% its tax, TOTAL what was paid.
        // "VALOR TOTAL" repeats the total plus the IVA, as if it were a total with tax added.
        val parsed = ReceiptParser.parse(
            listOf(
                row("CODIGO DESCR.", "CANT. P.UNIT. VALOR"),
                row("000178 MADURO", "2.87"),
                row("000201 LECHE", "1.15"),
                row("SUBTOTAL 15%", "3.50"),
                row("IVA 15%", "0.52"),
                row("TOTAL", "4.02"),
                row("VALOR TOTAL", "4.54"),
                row("EFECTIVO", "4.02"),
            )
        )
        assertNull(parsed.taxCents)
        assertEquals(402L, parsed.totalCents)
        assertTrue(parsed.isConsistent)
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
    fun `brazilian NFC-e - codes, quantity lines with the line total, tax notice and change`() {
        // Made-up store and values, in the usual NFC-e layout (no real receipt yet).
        val parsed = ReceiptParser.parse(
            listOf(
                row("SUPERMERCADO EXEMPLO LTDA"),
                row("CNPJ: 11.222.333/0001-81"),
                row("CÓDIGO DESCRIÇÃO QTDE UN VL.UNIT VL.TOTAL"),
                row("001 7891000100103 LEITE INTEGRAL 1L"),
                row("2 UN X 4,99", "9,98"),
                row("002 7896000000017 ARROZ TIPO 1 5KG 1UN X 22,90", "22,90"),
                row("003 7890000000024 BANANA PRATA", "3,49"),
                row("QTD. TOTAL DE ITENS", "4"),
                row("VALOR TOTAL R$", "36,37"),
                row("FORMA DE PAGAMENTO VALOR PAGO R$"),
                row("Dinheiro", "50,00"),
                row("TROCO R$", "13,63"),
                row("Tributos Totais Incidentes (Lei Federal 12.741/2012)", "R$7,12"),
                row("CONSUMIDOR - CPF 123.456.789-00"),
                row("Emissão: 21/11/2026 10:15:32"),
            )
        )
        assertEquals(listOf("LEITE INTEGRAL 1L", "ARROZ TIPO 1 5KG", "BANANA PRATA"), parsed.items.map { it.name })
        assertEquals(listOf("7891000100103", "7896000000017", "7890000000024"), parsed.items.map { it.code })
        assertEquals(listOf(998L, 2290L, 349L), parsed.items.map { it.priceCents })
        assertEquals(listOf(2, 1, 1), parsed.items.map { it.quantity })
        assertEquals(3637L, parsed.totalCents)
        assertTrue(parsed.isConsistent)
        assertNull(parsed.taxCents)
        assertEquals(ReceiptDate(2026, 11, 21, 10, 15), parsed.date)
        assertEquals("11222333000181", parsed.vatNumber)
    }

    @Test
    fun `quantity inside a brazilian item line is taken out of the name`() {
        val parsed = ReceiptParser.parse(
            listOf(
                row("DESCRIÇÃO", "VL.TOTAL"),
                row("LEITE INTEGRAL 3 UN 4,50", "13,50"),
                row("VALOR TOTAL R$", "13,50"),
            )
        )
        val item = parsed.items.single()
        assertEquals("LEITE INTEGRAL", item.name)
        assertEquals(3, item.quantity)
        assertEquals(1350L, item.priceCents)
    }

    @Test
    fun `portuguese receipt - store NIF, discount, Multibanco, IVA included`() {
        // Made-up store and values.
        val parsed = ReceiptParser.parse(
            listOf(
                row("MERCADO EXEMPLO, S.A."),
                row("NIF: 500 100 144"),
                row("Descrição", "IVA Valor"),
                row("LEITE MEIO GORDO", "C 0,79"),
                row("2 X 0,79"),
                row("PÃO DE FORMA", "1,58 C"),
                row("Desconto Imediato", "-0,30"),
                row("TOTAL A PAGAR", "2,07"),
                row("Multibanco", "2,07"),
                row("Troco", "0,00"),
                row("C 6% 1,95 0,12"),
                row("Contribuinte n.º: 999999990"),
                row("2026-11-21 18:40"),
            )
        )
        assertEquals(listOf("LEITE MEIO GORDO", "PÃO DE FORMA"), parsed.items.map { it.name })
        assertEquals(listOf(1, 2), parsed.items.map { it.quantity })
        assertEquals(30L, parsed.items[1].discountCents)
        assertEquals(207L, parsed.totalCents)
        assertTrue(parsed.isConsistent)
        assertNull(parsed.taxCents)
        assertEquals("500100144", parsed.vatNumber)
        assertEquals(ReceiptDate(2026, 11, 21, 18, 40), parsed.date)
    }

    @Test
    fun `portuguese NIF of the customer or with a wrong check digit is not the store's`() {
        fun vat(line: String) = ReceiptParser.parse(listOf(row(line), row("PAO", "1,00"), row("TOTAL", "1,00"))).vatNumber
        assertEquals("500100144", vat("Contribuinte 500100144"))
        assertNull(vat("NIF Cliente: 500100144"))
        assertNull(vat("NIF: 500100145"))
        assertNull(vat("Contribuinte: 999999990"))
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
