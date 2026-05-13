package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.Receipt

@Dao
interface ReceiptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: Receipt): Long

    @Update
    suspend fun update(receipt: Receipt)

    @Delete
    suspend fun delete(receipt: Receipt)

    @Query("SELECT * FROM Receipt ORDER BY date DESC")
    suspend fun getAll(): List<Receipt>

    @Query("SELECT * FROM Receipt WHERE id = :id")
    suspend fun getById(id: Int): Receipt?

    @Query("SELECT * FROM Receipt WHERE storeId = :storeId ORDER BY date DESC")
    suspend fun getByStore(storeId: Int): List<Receipt>

    @Query("SELECT * FROM Receipt WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    suspend fun getByDateRange(from: Long, to: Long): List<Receipt>

    @Query("SELECT SUM(total) FROM Receipt WHERE date BETWEEN :from AND :to")
    suspend fun getTotalSpentInRange(from: Long, to: Long): Double?
}
