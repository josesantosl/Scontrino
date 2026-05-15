package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.dao.ItemDao
import com.pepesantos.scontrino.data.dao.ReceiptDao
import com.pepesantos.scontrino.data.model.Item
import com.pepesantos.scontrino.data.model.Receipt

class ReceiptRepository(
    private val receiptDao: ReceiptDao,
    private val itemDao: ItemDao
) {
    suspend fun insert(receipt: Receipt, items: List<Item>) {
        val receiptId = receiptDao.insert(receipt)
        val itemsWithReceiptId = items.map { it.copy(receiptId = receiptId.toInt()) }
        itemDao.insertAll(itemsWithReceiptId)
    }

    suspend fun update(receipt: Receipt) = receiptDao.update(receipt)

    suspend fun delete(receipt: Receipt) = receiptDao.delete(receipt)

    suspend fun getAll() = receiptDao.getAllWithStoreName()

    //suspend fun getById(id: Int) = receiptDao.getById(id)
    suspend fun getById(id: Int) = receiptDao.getByIdWithStoreName(id)
    suspend fun getByStore(storeId: Int) = receiptDao.getByStore(storeId)

    suspend fun getByDateRange(from: Long, to: Long) = receiptDao.getByDateRange(from, to)

    suspend fun getTotalSpentInRange(from: Long, to: Long) = receiptDao.getTotalSpentInRange(from, to)
}