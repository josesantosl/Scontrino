package com.pepesantos.scontrino.data.repository

import com.pepesantos.scontrino.data.alias.AliasRules
import com.pepesantos.scontrino.data.dao.AliasDao
import com.pepesantos.scontrino.data.dao.ProductDao
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.ProductAlias

class ProductRepository(
    private val productDao: ProductDao,
    private val aliasDao: AliasDao,
) {

    suspend fun insert(product: Product) = productDao.insert(product)

    suspend fun update(product: Product) = productDao.update(product)

    suspend fun delete(product: Product) = productDao.delete(product)

    suspend fun getAll() = productDao.getAll()

    suspend fun getById(id: Int) = productDao.getById(id)

    suspend fun search(query: String) = productDao.search("%$query%")

    suspend fun getByCategory(categoryId: Int) = productDao.getByCategory(categoryId)

    /** Products bought at least once, with how often. */
    suspend fun getUsage() = productDao.getUsage()

    suspend fun getAliases() = aliasDao.getAll()

    /**
     * Sets (or removes, with null/blank) the alias of a product following [AliasRules]:
     * reuses existing spellings, never chains aliases, and when another product's name
     * is picked, that product joins the group too. The product takes the category of the
     * alias; a new alias (or one still in "other") takes the product's category.
     */
    suspend fun setAlias(productId: Int, alias: String?) {
        val products = productDao.getAll()
        val self = products.find { it.id == productId } ?: return
        val resolution = AliasRules.resolve(alias, products, selfId = productId)
        val name = resolution.alias
        if (name == null) {
            productDao.setAlias(productId, null)
            aliasDao.deleteUnused()
            return
        }
        val promoted = resolution.promotedProductId?.let { id -> products.find { it.id == id } }
        val existing = aliasDao.get(name)
        val categoryId = existing?.categoryId?.takeIf { it != OTHER_CATEGORY_ID }
            ?: promoted?.categoryId?.takeIf { it != OTHER_CATEGORY_ID }
            ?: self.categoryId
        aliasDao.upsert(ProductAlias(name, categoryId))
        productDao.setAlias(productId, name)
        promoted?.let { productDao.setAlias(it.id, it.name) }
        productDao.setCategoryForAlias(name, categoryId)
        aliasDao.deleteUnused()
    }

    /** Changes the category of an alias, and so of every product grouped under it. */
    suspend fun setAliasCategory(alias: String, categoryId: Int) {
        aliasDao.setCategory(alias, categoryId)
        productDao.setCategoryForAlias(alias, categoryId)
    }

    /**
     * Product of a receipt row called [name]: found (case-insensitive) or created.
     * - [alias]: null leaves the product's alias as it is, blank removes it, otherwise
     *   it is set (see [setAlias]).
     * - [categoryId]: applied unless it is "other"; for a product with alias it changes
     *   the alias's category, i.e. the whole group.
     */
    suspend fun getOrCreate(name: String, categoryId: Int = OTHER_CATEGORY_ID, alias: String? = null): Product {
        val results = productDao.search("%$name%")
        var product = results.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: Product(name = name, categoryId = categoryId).let { new ->
                new.copy(id = productDao.insert(new).toInt())
            }

        if (alias != null && AliasRules.normalize(alias) != product.alias) {
            setAlias(product.id, alias)
            product = productDao.getById(product.id) ?: product
        }

        if (categoryId != OTHER_CATEGORY_ID && categoryId != product.categoryId) {
            val groupAlias = product.alias
            if (groupAlias != null) {
                setAliasCategory(groupAlias, categoryId)
            } else {
                productDao.setCategory(product.id, categoryId)
            }
            product = product.copy(categoryId = categoryId)
        }
        return product
    }

    private companion object {
        const val OTHER_CATEGORY_ID = 1
    }
}
