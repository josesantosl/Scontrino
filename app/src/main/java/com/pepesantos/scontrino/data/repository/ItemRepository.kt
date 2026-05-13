package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.dao.ItemDao
import com.pepesantos.scontrino.data.model.Item

class ItemRepository(private val itemDao: ItemDao) {

    suspend fun insert(item: Item) = itemDao.insert(item)

    suspend fun insertAll(items: List<Item>) = itemDao.insertAll(items)

    suspend fun update(item: Item) = itemDao.update(item)

    suspend fun delete(item: Item) = itemDao.delete(item)

    suspend fun getByReceipt(receiptId: Int) = itemDao.getByReceipt(receiptId)

    suspend fun getByProduct(productId: Int) = itemDao.getByProduct(productId)

    suspend fun deleteByReceipt(receiptId: Int) = itemDao.deleteByReceipt(receiptId)
}