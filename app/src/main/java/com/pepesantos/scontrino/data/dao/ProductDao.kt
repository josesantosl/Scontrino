package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.Product

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
}