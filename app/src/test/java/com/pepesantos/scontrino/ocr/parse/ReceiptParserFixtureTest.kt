package com.pepesantos.scontrino.ocr.parse

import com.pepesantos.scontrino.ocr.OcrFixtures
import com.pepesantos.scontrino.ocr.ReceiptRowBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole pipeline after OCR (rows → parser) on real receipts. */
class ReceiptParserFixtureTest {

    private fun parse(fixture: String) =
        ReceiptParser.parse(ReceiptRowBuilder.build(OcrFixtures.words(fixture), useEngineLines = true))

    @Test
    fun `lidl - all items, the corrected misread and the date`() {
        val receipt = parse("lidl-torino-2026-08-17-lines")

        assertEquals(
            listOf(
                "SUCCO DI ARANCIA" to "3,49",
                "COLA" to "0,55",
                "FALAFEL AGLIO" to "1,99",
                "FALAFEL PICCANTE" to "1,99",
                "CRACKERS INTEGRALI" to "1,19",
                "PASTA ISTANTANEA" to "1,99",
                "VASETTI SPEZIE CURRY" to "2,49",
                "HUMMUS CURRY" to "1,19",
                "NIPPON QUADRATINI" to "1,79",
            ),
            receipt.items.map { it.name to Amounts.format(it.priceCents) }
        )
        // Read as 11,99: the only price one digit away from making the total add up.
        val piccante = receipt.items.single { it.name == "FALAFEL PICCANTE" }
        assertEquals(1199L, piccante.correctedFromCents)

        assertEquals(1667L, receipt.totalCents)
        assertTrue(receipt.isConsistent)
        assertEquals(ReceiptDate(2026, 8, 17, 17, 24), receipt.date)
        assertEquals(22.0, receipt.items.first().vatRate!!, 0.0)
    }

    @Test
    fun `bennet - discounts, VAT letters, codes and the misread total`() {
        val receipt = parse("bennet-torino-san-paolo-lines")

        assertEquals(
            listOf(
                Triple("PIZZA POMODORISSIM", "1,84", 0L),
                Triple("CONT.LEGGEREZZA G", "2,99", 90L),
                Triple("CONT.LEGGEREZZA G", "2,99", 90L),
                Triple("SOVADRINK CLASSICO LT 1", "2,49", 75L),
                Triple("FARINATA", "2,35", 0L),
                Triple("CATISFACTION SALMONE GR", "3,99", 0L),
                Triple("NATU' GATTO POLLO GR", "0,99", 0L),
                Triple("NATU' GATTO POLLO GR", "0,99", 0L),
                Triple("GRAN CEREALE CIOC. MB GR", "1,60", 0L),
            ),
            receipt.items.map { Triple(it.name, Amounts.format(it.priceCents), it.discountCents) }
        )
        assertEquals(listOf("255", "353406", "353406", null, "349"), receipt.items.take(5).map { it.code })
        assertEquals(listOf("C", "B", "B", "D", "C"), receipt.items.take(5).map { it.vatCode })

        // Printed 2,35, read 2,39; found among seven one-digit candidates by confidence.
        assertEquals(239L, receipt.items.single { it.name == "FARINATA" }.correctedFromCents)
        // The large total was read as "1768"; the payment lines give 17,68.
        assertEquals(1768L, receipt.totalCents)
        assertTrue(receipt.isConsistent)
        assertEquals("07071700152", receipt.vatNumber)
    }

    @Test
    fun `bennet 2026-11-07 - consistent as read, with VAT legend and date`() {
        val receipt = parse("bennet-torino-san-paolo-2026-11-07")

        assertEquals(
            listOf(
                "CONT .LEGGEREZZA G", "LASAGNE RAGU SOIA BENN G", "HUMMUS ZORBAS GR 200",
                "BIA COUSCOUS MEDIO INT G", "GERMOGLI DI SOIA G 150", "INSALATA MISTA BUSTA G 1",
                "FOCACCIA CIPOLLA", "CORNETTO VEGANO CEREALI", "KINDER BUENOWHITET2X3 GR",
            ),
            receipt.items.map { it.name }
        )
        assertEquals(listOf(90L, 0L, 0L, 46L), receipt.items.take(4).map { it.discountCents })
        assertTrue(receipt.items.all { it.correctedFromCents == null })

        assertEquals(1959L, receipt.totalCents)
        assertTrue(receipt.isConsistent)
        // Letters resolved through the "B: IVA 4,0%" / "C: IVA 10,0%" legend.
        assertEquals(4.0, receipt.items.first().vatRate!!, 0.0)
        assertEquals(10.0, receipt.items[1].vatRate!!, 0.0)
        assertEquals(ReceiptDate(2026, 11, 7, 9, 40), receipt.date)
        assertEquals("07071700152", receipt.vatNumber)
        assertNull(receipt.items.first { it.name.startsWith("BIA") }.code)
    }

    @Test
    fun `bennet 2026-11-08 - clean read, total from the large font, VAT letters D and C`() {
        val receipt = parse("bennet-torino-san-paolo-2026-11-08")

        assertEquals(
            listOf(
                Triple("SACC.UMIDO BENNET 10LT P", "1,50", 0L),
                Triple("SAC .TRASP.BENNET 110LT P", "1,99", 0L),
                Triple("SOYADRINK CLASSICO LT 1", "2,49", 75L),
                Triple("VERDURE ORTO PASTELLA", "2,32", 0L),
                Triple("PIZZA POMODORISSIM", "1,35", 0L),
            ),
            receipt.items.map { Triple(it.name, Amounts.format(it.priceCents), it.discountCents) }
        )
        assertEquals("265", receipt.items.last().code)
        assertTrue(receipt.items.all { it.correctedFromCents == null })

        assertEquals(890L, receipt.totalCents)
        assertTrue(receipt.isConsistent)
        // "D: IVA 22,0%" / "C: IVA 10,0%".
        assertEquals(listOf(22.0, 22.0, 22.0, 10.0, 10.0), receipt.items.map { it.vatRate })
        assertEquals(ReceiptDate(2026, 11, 8, 14, 30), receipt.date)
        assertEquals("07071700152", receipt.vatNumber)
    }

    @Test
    fun `carrefour 2026-11-09 - quantity line before the item, noise at the left edge`() {
        val receipt = parse("carrefour-torino-2026-11-09")

        assertEquals(
            listOf(
                "DRINK SOIA CRF BIO 1" to "2,19",
                "FARINA\"00\" CRF KG1" to "1,58",
                "SEITAN NATURALE GR90" to "4,29",
            ),
            receipt.items.map { it.name to Amounts.format(it.priceCents) }
        )
        assertTrue(receipt.items.all { it.correctedFromCents == null })
        // "2 x 0,79" is printed on its own line, before the flour.
        assertEquals(listOf(1, 2, 1), receipt.items.map { it.quantity })
        // The VAT column holds plain numbers ("22,00"); the flour's 4,00 slid onto
        // the quantity line.
        assertEquals(listOf(22.0, 4.0, 10.0), receipt.items.map { it.vatRate })
        assertTrue(receipt.unparsedRows.isEmpty())

        assertEquals(806L, receipt.totalCents)
        assertTrue(receipt.isConsistent)
        assertEquals(ReceiptDate(2026, 11, 9, 19, 55), receipt.date)
        assertEquals("02924950963", receipt.vatNumber)
    }

    @Test
    fun `carrefour 2026-11-09 retake - misread quantity line with a speck and a doubled sign`() {
        // Printed "2 X 0,79", read "2. xX 0,79".
        val receipt = parse("carrefour-torino-2026-11-09-retake")

        assertEquals(
            listOf(
                Triple("DRINK SOIA CRF BIO 1", "2,19", 1),
                Triple("FARINA\"00\" CRF KG1", "1,58", 2),
                Triple("SEITAN NATURALE GR30", "4,29", 1),
            ),
            receipt.items.map { Triple(it.name, Amounts.format(it.priceCents), it.quantity) }
        )
        // The flour's VAT was cut short to "04,": unknown, but not taken as a price.
        assertEquals(listOf(22.0, null, 10.0), receipt.items.map { it.vatRate })
        assertTrue(receipt.unparsedRows.isEmpty())

        assertEquals(806L, receipt.totalCents)
        assertTrue(receipt.isConsistent)
    }
}
