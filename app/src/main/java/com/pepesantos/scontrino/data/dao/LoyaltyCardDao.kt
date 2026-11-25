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
        ORDER BY LoyaltyCard.position ASC, Store.name ASC
    """)
    suspend fun getAllWithStore(): List<LoyaltyCardWithStore>

    @Query("SELECT * FROM LoyaltyCard WHERE id = :id")
    suspend fun getById(id: Int): LoyaltyCard?

    /** Position after the last card, for a new one (0 when the wallet is empty). */
    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM LoyaltyCard")
    suspend fun nextPosition(): Int

    @Query("UPDATE LoyaltyCard SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Int, position: Int)

    /** Saves the order of the whole wallet at once: [positions] maps card id to position. */
    @Transaction
    suspend fun setPositions(positions: Map<Int, Int>) {
        positions.forEach { (id, position) -> setPosition(id, position) }
    }
}
