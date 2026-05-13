package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.dao.CategoryDao
import com.pepesantos.scontrino.data.model.Category

class CategoryRepository(private val categoryDao: CategoryDao) {

    suspend fun insert(category: Category) = categoryDao.insert(category)

    suspend fun update(category: Category) = categoryDao.update(category)

    suspend fun delete(category: Category) = categoryDao.delete(category)

    suspend fun getAll() = categoryDao.getAll()

    suspend fun getById(id: Int) = categoryDao.getById(id)

    suspend fun getCustom() = categoryDao.getCustom()
}