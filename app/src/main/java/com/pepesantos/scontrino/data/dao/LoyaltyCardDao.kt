package com.pepesantos.scontrino.data.dao

import androidx.room.*
import com.pepesantos.scontrino.data.model.LoyaltyCard
import com.pepesantos.scontrino.data.model.LoyaltyCardWithStore

@Dao
interface LoyaltyCardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: LoyaltyCard): Long

    @Update
    suspend fun update(card: LoyaltyCard)

    @Delete
    suspend fun delete(card: LoyaltyCard)

    @Query("""
        SELECT LoyaltyCard.*, Store.name AS storeName, Store.color AS storeColor
        FROM LoyaltyCard
        INNER JOIN Store ON LoyaltyCard.storeId = Store.id
        ORDER BY Store.name ASC
    """)
    suspend fun getAllWithStore(): List<LoyaltyCardWithStore>

    @Query("SELECT * FROM LoyaltyCard WHERE id = :id")
    suspend fun getById(id: Int): LoyaltyCard?
}
