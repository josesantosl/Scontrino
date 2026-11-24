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
        // Printed "PI02275030233", read "PIQ2275030233".
        assertEquals("02275030233", receipt.vatNumber)
    }

    @Test
    fun `lidl 2026-11-20 - Cad Pz quantity lines after the item, slashed zeros`() {
        val receipt = parse("lidl-torino-2026-11-20")

        assertEquals(
            listOf(
                Triple("BEV.SOIA CALCIO VITA", "1,98", 2),
                Triple("CONTORNO DI VERDURE", "3,98", 2),
                Triple("SALSA A BASE DI SOIA", "0,89", 1),
                Triple("TOFU BIO NATURALE", "1,78", 2),
                Triple("NEO BISC.V.CACAO VAN", "0,89", 1),
                Triple("HUMMUS CAROTE ZENZER", "1,49", 1),
                Triple("WHISKAS TEMPTAT POLL", "1,59", 1),
                Triple("RUCOLA", "0,99", 1),
                Triple("SNACK PATATE PAPRICA", "1,59", 1),
            ),
            receipt.items.map { Triple(it.name, Amounts.format(it.priceCents), it.quantity) }
        )
        // "Cad 9,99 Pz. 2" and "Cad 09,89 Pz. 2": unit price one character off, still
        // the quantity of the item above. The speck "se" goes with its line.
        assertTrue(receipt.unparsedRows.isEmpty())
        // Printed 0,99, read 8,99.
        assertEquals(899L, receipt.items.single { it.name == "RUCOLA" }.correctedFromCents)
        assertTrue(receipt.items.filter { it.name != "RUCOLA" }.all { it.correctedFromCents == null })

        assertEquals(1518L, receipt.totalCents)
        assertTrue(receipt.isConsistent)
        assertNull(receipt.taxCents)
        // "20-11-2926 15:99".
        assertEquals(ReceiptDate(2026, 11, 20, 15, 9), receipt.date)
        // "PIG22 75030233", fixed through the check digit.
        assertEquals("02275030233", receipt.vatNumber)
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
        // "DI CUI IVA 0,84" is VAT included in the prices, not added to them.
        assertNull(receipt.taxCents)
        assertEquals(ReceiptDate(2026, 11, 9, 19, 55), receipt.date)
        assertEquals("02924950963", receipt.vatNumber)
    }

    @Test
    fun `mercadona 2022-09-24 - units column, weight lines, unlabelled CIF`() {
        val receipt = parse("mercadona-alcorcon-2022-09-24")

        assertEquals(
            listOf(
                "CANÓNIGOS" to "1,19",
                "ICEBERG 250 GR" to "0,73", // printed 0,79
                "ENS.IV ESTACIONES" to "0,69",
                "ZANAHORIA 500 G" to "0,39", // printed 0,59
                "PROTEÍNAS FRESA" to "1,50",
                "CHAMPIÑON BANDEJA P" to "1,25",
                "+PROT NATILLA VAINI" to "1,60",
                "PATATA GUARN. 1 KG" to "2,15",
                "PROTEÍNA ARANDANOS" to "1,50",
                "PEPINO HOLANDES" to "0,91",
                "CALABAZA TROZO" to "1,68",
                "AGUACATE BANDEJA" to "2,39",
                "UVA BLANCA S/SEM" to "2,78",
                "12 HUEVOS CAMPEROS" to "2,65",
                "MANTEQUILLA PAST.SIN" to "1,95",
                "BARRITA PERICO MIEL" to "1,60",
                "PATAS POLLO" to "1,98",
                "SOLOMILLO CERTIFICAD" to "4,76",
                "POLLO CERTIFICADO E" to "6,93",
                "PLATANO" to "4,34",
                "CHIRIMOYA" to "3,20",
                "CALABACIN VERDE" to "2,30",
            ),
            receipt.items.map { it.name to Amounts.format(it.priceCents) }
        )
        // The units column is the quantity, not part of the name ("1 12 HUEVOS").
        assertTrue(receipt.items.all { it.quantity == 1 })
        // "1,378 kg  3,15 €/kg  4,34" under the name.
        assertEquals(
            listOf(1378.0, 1420.0, 1444.0),
            receipt.items.takeLast(3).map { it.weightGrams }
        )
        assertTrue(receipt.items.dropLast(3).all { it.weightGrams == null })
        assertTrue(receipt.unparsedRows.isEmpty())

        // The large total and card lines both read 48,13; the VAT table's TOTAL row
        // (45,72 + 3,01) gives the printed 48,73. Two prices are still misread, so it
        // can't add up, and no single price is "corrected" into a wrong value.
        assertEquals(4873L, receipt.totalCents)
        assertTrue(!receipt.isConsistent)
        assertTrue(receipt.items.all { it.correctedFromCents == null })
        assertNull(receipt.taxCents)
        assertEquals(ReceiptDate(2022, 9, 24, 11, 41), receipt.date)
        // "A- 46103834", alone under the store name.
        assertEquals("A46103834", receipt.vatNumber)
    }

    @Test
    fun `tal apotheke 2019-01-30 - German Summe, count before the name, code and unit price below`() {
        val receipt = parse("tal-apotheke-trebbus-2019-01-30")

        // "1 * IBU 400 ..." / "03045316  Einzelpreis  EUR 9,45".
        val item = receipt.items.single()
        assertEquals("IBU 400 AKUT 1A PHARMA FTA 50St", item.name)
        assertEquals("03045316", item.code)
        assertEquals(945L, item.priceCents)
        assertEquals(1, item.quantity)
        assertEquals(listOf(13, 14), item.rows)

        // "Summe" closes the item list; "Nettosumme" and "incl. MWST" are not totals.
        assertEquals(945L, receipt.totalCents)
        assertTrue(receipt.isConsistent)
        assertNull(receipt.taxCents)
        assertTrue(receipt.footerLines.first().startsWith("Summe"))
        assertEquals(ReceiptDate(2019, 1, 30, 14, 33), receipt.date)
        // "USt-IdNr.: DEO00000000": the slashed zero read as O.
        assertEquals("DE000000000", receipt.vatNumber)
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
