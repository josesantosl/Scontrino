package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.dao.ItemDao
import com.pepesantos.scontrino.data.dao.ReceiptDao
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.Receipt
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ReceiptRepositoryTest {

    private val receiptDao: ReceiptDao = mock()
    private val itemDao: ItemDao = mock()
    private lateinit var repository: ReceiptRepository

    @Before
    fun setup() {
        repository = ReceiptRepository(receiptDao, itemDao)
    }

    @Test
    fun `insert saves receipt and its items with correct id`() = runTest {
        val receipt = Receipt(id = 0, date = 100L, total = 20.0, storeId = 1)
        val items = listOf(
            Item(id = 0, productId = 1, price = 10.0, quantity = 2, receiptId = 0)
        )
        
        whenever(receiptDao.insert(receipt)).thenReturn(5L)

        repository.insert(receipt, items)

        verify(receiptDao).insert(receipt)
        verify(itemDao).insertAll(argThat { 
            size == 1 && first().receiptId == 5
        })
    }
}
