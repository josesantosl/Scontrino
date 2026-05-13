package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.LoyaltyCard

@Dao
interface LoyaltyCardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: LoyaltyCard): Long

    @Update
    suspend fun update(card: LoyaltyCard)

    @Delete
    suspend fun delete(card: LoyaltyCard)

    @Query("SELECT * FROM LoyaltyCard ORDER BY name ASC")
    suspend fun getAll(): List<LoyaltyCard>

    @Query("SELECT * FROM LoyaltyCard WHERE id = :id")
    suspend fun getById(id: Int): LoyaltyCard?
}