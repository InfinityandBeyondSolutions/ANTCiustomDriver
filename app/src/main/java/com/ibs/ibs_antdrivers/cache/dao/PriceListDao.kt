package com.ibs.ibs_antdrivers.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ibs.ibs_antdrivers.cache.entities.PriceListEntity
import com.ibs.ibs_antdrivers.cache.entities.PriceListItemEntity
import com.ibs.ibs_antdrivers.cache.entities.PriceListWithItems
import kotlinx.coroutines.flow.Flow

/** Lightweight list row for showing item counts on the Price Lists screen. */
data class PriceListWithCount(
    val priceListId: String,
    val title: String,
    val name: String,
    val companyName: String,
    val effectiveDate: String,
    val status: String,
    val includeVat: Boolean,
    val updatedAt: String,
    val createdAt: String,
    val itemCount: Int,
)

@Dao
interface PriceListDao {

    @Query("SELECT * FROM price_lists ORDER BY CASE WHEN title != '' THEN title ELSE name END COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PriceListEntity>>

    @Transaction
    @Query("SELECT * FROM price_lists WHERE priceListId = :id LIMIT 1")
    fun observeWithItems(id: String): Flow<PriceListWithItems?>

    @Query(
        """
        SELECT pl.priceListId,
               pl.title,
               pl.name,
               pl.companyName,
               pl.effectiveDate,
               pl.status,
               pl.includeVat,
               pl.updatedAt,
               pl.createdAt,
               COUNT(i.itemId) AS itemCount
        FROM price_lists pl
        LEFT JOIN price_list_items i ON i.priceListId = pl.priceListId
        GROUP BY pl.priceListId
        ORDER BY CASE WHEN pl.title != '' THEN pl.title ELSE pl.name END COLLATE NOCASE ASC
        """
    )
    fun observeAllWithItemCount(): Flow<List<PriceListWithCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLists(items: List<PriceListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<PriceListItemEntity>)

    @Query("DELETE FROM price_list_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM price_lists")
    suspend fun deleteAllLists()
}
