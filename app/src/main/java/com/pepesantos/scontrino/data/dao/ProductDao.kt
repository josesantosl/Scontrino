package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.Product
import com.pepesantos.scontrino.data.model.ProductUsage

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("SELECT * FROM Product ORDER BY name ASC")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM Product WHERE id = :id")
    suspend fun getById(id: Int): Product?

    @Query("SELECT * FROM Product WHERE name LIKE :query ORDER BY name ASC")
    suspend fun search(query: String): List<Product>

    @Query("SELECT * FROM Product WHERE categoryId = :categoryId ORDER BY name ASC")
    suspend fun getByCategory(categoryId: Int): List<Product>

    @Query("UPDATE Product SET alias = :alias WHERE id = :productId")
    suspend fun setAlias(productId: Int, alias: String?)

    @Query("UPDATE Product SET categoryId = :categoryId WHERE id = :productId")
    suspend fun setCategory(productId: Int, categoryId: Int)

    /** Keeps the products of an alias in the alias's category. */
    @Query("UPDATE Product SET categoryId = :categoryId WHERE alias = :alias")
    suspend fun setCategoryForAlias(alias: String, categoryId: Int)

    /** Products bought at least once, with how many receipt lines they appear in. */
    @Query("""
    SELECT Product.*, COUNT(Item.id) AS purchaseCount
    FROM Product
    INNER JOIN Item ON Item.productId = Product.id
    GROUP BY Product.id
""")
    suspend fun getUsage(): List<ProductUsage>
}
