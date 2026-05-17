package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.dao.ProductDao
import com.pepesantos.scontrino.data.model.Product

class ProductRepository(private val productDao: ProductDao) {

    suspend fun insert(product: Product) = productDao.insert(product)

    suspend fun update(product: Product) = productDao.update(product)

    suspend fun delete(product: Product) = productDao.delete(product)

    suspend fun getAll() = productDao.getAll()

    suspend fun getById(id: Int) = productDao.getById(id)

    suspend fun search(query: String) = productDao.search("%$query%")

    suspend fun getByCategory(categoryId: Int) = productDao.getByCategory(categoryId)

    suspend fun getOrCreate(name: String, categoryId: Int = 1): Product {
        val results = productDao.search("%$name%")
        val exact = results.firstOrNull { it.name.equals(name, ignoreCase = true) }
        return if (exact != null) {
            if (exact.categoryId != categoryId && categoryId != 1) {
                val updated = exact.copy(categoryId = categoryId)
                productDao.update(updated)
                updated
            } else {
                exact
            }
        } else {
            val newProduct = Product(name = name, categoryId = categoryId)
            val id = productDao.insert(newProduct)
            newProduct.copy(id = id.toInt())
        }
    }
}