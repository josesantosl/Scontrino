package com.pepesantos.scontrino.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row reconstruction on real OCR output (fixtures in src/test/resources/ocr). */
class ReceiptRowBuilderFixtureTest {

    private fun rowsOf(fixture: String, useEngineLines: Boolean = false) =
        ReceiptRowBuilder.build(OcrFixtures.words(fixture), useEngineLines = useEngineLines)

    /**
     * Walks the rows top to bottom and checks that each (name, amount) pair appears,
     * in order, with both on the same row. Order-based matching copes with repeated
     * names (the same product bought twice, several discount lines).
     */
    private fun assertRowsInOrder(rows: List<ReceiptRow>, expected: List<Pair<String, String>>) {
        var next = 0
        for ((name, amount) in expected) {
            val index = (next until rows.size).firstOrNull { name in rows[it].text }
            assertTrue(
                "No row containing \"$name\" after row $next: ${rows.map { it.text }}",
                index != null
            )
            val row = rows[index!!]
            assertTrue(
                "\"$name\" should be on the same row as \"$amount\", got \"${row.text}\"",
                row.words.any { it.text == amount }
            )
            next = index + 1
        }
    }

    @Test
    fun `lidl - every item and total is on the same row as its amount`() {
        assertRowsInOrder(
            rowsOf("lidl-torino-2026-08-17"),
            listOf(
                "SUCCO DI ARANCIA" to "3,49",
                // The OCR reads the slashed zero of 0,55 as 9; fixing that is the parser's job.
                "COLA" to "9,55",
                "FALAFEL AGLIO" to "1,99",
                "FALAFEL PICCANTE" to "1,99",
                "CRACKERS INTEGRALI" to "1,19",
                "PASTA ISTANTANEA" to "1,99",
                "VASETTI SPEZIE CURRY" to "2,49",
                "HUMMUS CURRY" to "1,19",
                "NIPPON QUADRATINI" to "1,79",
                "SUBTOTALE" to "16,67",
                "TOTALE COMPLESSIVO" to "16,67",
                "Importo pagato" to "16,67",
            )
        )
    }

    @Test
    fun `lidl - name and price end up in different segments`() {
        val row = rowsOf("lidl-torino-2026-08-17").single { "NIPPON QUADRATINI" in it.text }
        assertEquals(listOf("NIPPON QUADRATINI", "10%", "1,79"), row.segments)
    }

    @Test
    fun `bennet - items and discounts are on the same row as their amount`() {
        assertRowsInOrder(
            rowsOf("bennet-torino-san-paolo"),
            listOf(
                "PIZZA POMODORISSIM" to "1,84",
                "CONT.LEGGEREZZA" to "2,99",
                "Sconto Art. 30%" to "-0,90",
                "CONT.LEGGEREZZA" to "2,99",
                "Sconto Art. 30%" to "-0,90",
                "SOVADRINK CLASSICO LT 1" to "2,49",
                "Sconto Art. 30%" to "-0,75",
                // Printed 2,35; misread by the OCR. The parser can catch it: the sum is off.
                "FARINATA" to "2,39",
                "CATISFACTION SALMONE GR" to "3,99",
                "NATU' GATTO POLLO GR" to "0,99",
                "NATU' GATTO POLLO GR" to "0,99",
                "GRAN CEREALE CIOC. MB GR" to "1,60",
            )
        )
    }

    @Test
    fun `lidl with engine lines - every item and total is on the same row as its amount`() {
        assertRowsInOrder(
            rowsOf("lidl-torino-2026-08-17-lines", useEngineLines = true),
            listOf(
                "SUCCO DI ARANCIA" to "3,49",
                "COLA" to "0,55",
                "FALAFEL AGLIO" to "1,99",
                // Printed 1,99; misread by the OCR (the parser's sum check can catch it).
                "FALAFEL PICCANTE" to "11,99",
                "CRACKERS INTEGRALI" to "1,19",
                "PASTA ISTANTANEA" to "1,99",
                "VASETTI SPEZIE CURRY" to "2,49",
                "HUMMUS CURRY" to "1,19",
                "NIPPON QUADRATINI" to "1,79",
                "SUBTOTALE" to "16,67",
                "TOTALE COMPLESSIVO" to "16,67",
                // Printed 1,82.
                "DI CUI IVA" to "4,82",
                "Pagamento elettronico" to "16,67",
                "Importo pagato" to "16,67",
                "Bancomat" to "16,67",
            )
        )
    }

    @Test
    fun `bennet with engine lines - items, discounts and the bottom block keep their amount`() {
        val rows = rowsOf("bennet-torino-san-paolo-lines", useEngineLines = true)
        assertRowsInOrder(
            rows,
            listOf(
                "PIZZA POMODORISSIM" to "1,84",
                "CONT.LEGGEREZZA" to "2,99",
                "Sconto Art. 30%" to "-0,90",
                "CONT.LEGGEREZZA" to "2,99",
                "Sconto Art. 30%" to "~0,90",
                "SOVADRINK CLASSICO LT 1" to "2,49",
                "Sconto Art. 30%" to "-0,75",
                "FARINATA" to "2,39",
                "CATISFACTION SALMONE GR" to "3,99",
                "NATU' GATTO POLLO GR" to "0,99",
                "NATU' GATTO POLLO GR" to "0,99",
                "GRAN CEREALE CIOC. MB GR" to "1,60",
                // Large condensed font: printed 17,68.
                "TOTALE COMPLESSIVO" to "1768",
                "DI CUI IVA" to "2.08",
                "PAGAMENTO ELETTRONICO" to "17,68",
                "RESTO" to "0,00",
                "IMPORTO PAGATO" to "17,68",
            )
        )
        val pizza = rows.single { "PIZZA" in it.text }
        assertEquals(listOf("255 PIZZA POMODORISSIM", "1,84 C"), pizza.segments)
    }

    @Test
    fun `bennet 2026-11-07 - engine lines pair items, discounts and totals with their amount`() {
        val rows = rowsOf("bennet-torino-san-paolo-2026-11-07", useEngineLines = true)
        assertRowsInOrder(
            rows,
            listOf(
                "Torino San Paolo (TO), P. IVA" to "07071700152",
                ".LEGGEREZZA" to "2,99",
                "Sconto Art. 30%" to "-0,90",
                "LASAGNE RAGU SOIA" to "2,99",
                "HUMMUS ZORBAS" to "2,98",
                "COUSCOUS MEDIO" to "2,29",
                "Sconto Art. 20%" to "-0,46",
                "GERMOGLI DI SOIA" to "1,39",
                "INSALATA MISTA BUSTA" to "0,99",
                "FOCACCIA CIPOLLA" to "2,49",
                "CORNETTO VEGANO CEREALI" to "2,14",
                "KINDER" to "2,69",
                "DI CUI IVA" to "1.45",
                "PAGAMENTO ELETTRONICO" to "19,59",
            )
        )
        // Price and VAT letter stay together, apart from the name.
        val lasagne = rows.single { "LASAGNE" in it.text }
        assertEquals(listOf("LASAGNE RAGU SOIA BENN G", "2,99 C"), lasagne.segments)
    }

    @Test
    fun `bennet - multi-line junk from the VAT column does not merge item rows`() {
        val rows = rowsOf("bennet-torino-san-paolo")
        val pizza = rows.single { "PIZZA POMODORISSIM" in it.text }
        assertTrue("PIZZA row swallowed the next item: ${pizza.text}", "CONT.LEGGEREZZA" !in pizza.text)
    }
}
