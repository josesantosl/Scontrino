package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.alias.AliasRules
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

    /** Products bought at least once, with how often. */
    suspend fun getUsage() = productDao.getUsage()

    /**
     * Sets (or removes, with null/blank) the alias of a product following [AliasRules]:
     * reuses existing spellings, never chains aliases, and when another product's name
     * is picked, that product joins the group too. A product still in "other" takes the
     * category of its new group.
     */
    suspend fun setAlias(productId: Int, alias: String?) {
        val products = productDao.getAll()
        val resolution = AliasRules.resolve(alias, products, selfId = productId)
        productDao.setAlias(productId, resolution.alias)
        val self = products.find { it.id == productId }
        if (self != null && self.categoryId == OTHER_CATEGORY_ID && resolution.alias != null) {
            AliasRules.groupCategoryId(resolution.alias, products.filter { it.id != productId })
                ?.let { productDao.setCategory(productId, it) }
        }
        resolution.promotedProductId?.let { id ->
            products.find { it.id == id }?.let { productDao.setAlias(id, it.name) }
        }
    }

    private companion object {
        const val OTHER_CATEGORY_ID = 1
    }

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
