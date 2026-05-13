package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.dao.StoreDao
import com.pepesantos.scontrino.data.model.Store

class StoreRepository(private val storeDao: StoreDao) {

    suspend fun insert(store: Store) = storeDao.insert(store)

    suspend fun update(store: Store) = storeDao.update(store)

    suspend fun delete(store: Store) = storeDao.delete(store)

    suspend fun getAll() = storeDao.getAll()

    suspend fun getById(id: Int) = storeDao.getById(id)

    suspend fun search(query: String) = storeDao.search("%$query%")
}