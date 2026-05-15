package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.dao.LoyaltyCardDao
import com.pepesantos.scontrino.data.model.LoyaltyCard

class LoyaltyCardRepository(private val loyaltyCardDao: LoyaltyCardDao) {

    suspend fun insert(card: LoyaltyCard) = loyaltyCardDao.insert(card)

    suspend fun update(card: LoyaltyCard) = loyaltyCardDao.update(card)

    suspend fun delete(card: LoyaltyCard) = loyaltyCardDao.delete(card)

    suspend fun getAll() = loyaltyCardDao.getAllWithStore()

    suspend fun getById(id: Int) = loyaltyCardDao.getById(id)
}
