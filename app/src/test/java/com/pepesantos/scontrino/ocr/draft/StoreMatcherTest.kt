package com.pepesantos.scontrino.ocr.draft

import com.pepesantos.scontrino.data.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreMatcherTest {

    private val lidl = Store(id = 1, name = "Lidl")
    private val bennet = Store(id = 2, name = "Bennet", vatNumber = "07071700152")
    private val carrefour = Store(id = 3, name = "Carrefour")
    private val carrefourExpress = Store(id = 4, name = "Carrefour Express")
    private val stores = listOf(lidl, bennet, carrefour, carrefourExpress)

    @Test
    fun `store name found in the receipt text`() {
        val match = StoreMatcher.match(stores, listOf("Lidl Italia S.r.l. a socio Unico", "Torino - cdc 2574"), null)
        assertEquals(lidl, match?.store)
        assertEquals(StoreMatcher.Reason.NAME, match?.reason)
    }

    @Test
    fun `punctuation and case do not matter`() {
        assertEquals(bennet, StoreMatcher.match(stores, listOf("bennet. ;"), null)?.store)
    }

    @Test
    fun `a name found in the text wins over the VAT number`() {
        // The P.IVA belongs to Bennet, but the text clearly says Lidl.
        val match = StoreMatcher.match(stores, listOf("LIDL ITALIA"), vatNumber = "07071700152")
        assertEquals(lidl, match?.store)
        assertEquals(StoreMatcher.Reason.NAME, match?.reason)
    }

    @Test
    fun `the VAT number is used when no name is found`() {
        val match = StoreMatcher.match(stores, listOf("@@@ unreadable logo", "Via Renier 58"), vatNumber = "07071700152")
        assertEquals(bennet, match?.store)
        assertEquals(StoreMatcher.Reason.VAT_NUMBER, match?.reason)
    }

    @Test
    fun `one misread character is tolerated in longer names`() {
        assertEquals(bennet, StoreMatcher.match(stores, listOf("BENNFT CLUB"), null)?.store)
    }

    @Test
    fun `short names must match exactly`() {
        // "LDL" is one character away from "LIDL", but four letters is too short to guess.
        assertNull(StoreMatcher.match(stores, listOf("LDL."), null))
    }

    @Test
    fun `the most specific name wins`() {
        assertEquals(carrefourExpress, StoreMatcher.match(stores, listOf("CARREFOUR EXPRESS TORINO"), null)?.store)
    }

    @Test
    fun `the store mentioned most often wins`() {
        val match = StoreMatcher.match(stores, listOf("LIDL", "Buono sconto Bennet", "LIDL ITALIA"), null)
        assertEquals(lidl, match?.store)
    }

    @Test
    fun `names must appear as whole words`() {
        // "BENNETTI" (a surname, say) is not "Bennet": two extra letters.
        assertNull(StoreMatcher.match(stores, listOf("CASSIERE BENNETTI"), null))
    }

    @Test
    fun `nothing is found when there is no clue`() {
        assertNull(StoreMatcher.match(stores, listOf("DOCUMENTO COMMERCIALE"), vatNumber = "12345678901"))
    }
}
