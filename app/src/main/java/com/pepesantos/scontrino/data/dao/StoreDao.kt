package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.Store

@Dao
interface StoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(store: Store): Long

    @Update
    suspend fun update(store: Store)

    @Delete
    suspend fun delete(store: Store)

    @Query("SELECT * FROM Store ORDER BY name ASC")
    suspend fun getAll(): List<Store>

    @Query("SELECT * FROM Store WHERE id = :id")
    suspend fun getById(id: Int): Store?

    @Query("SELECT * FROM Store WHERE name LIKE :query")
    suspend fun search(query: String): List<Store>
}