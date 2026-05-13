package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.Category

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM Category ORDER BY label ASC")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM Category WHERE id = :id")
    suspend fun getById(id: Int): Category?

    @Query("SELECT * FROM Category WHERE isCustom = 1 ORDER BY label ASC")
    suspend fun getCustom(): List<Category>
}