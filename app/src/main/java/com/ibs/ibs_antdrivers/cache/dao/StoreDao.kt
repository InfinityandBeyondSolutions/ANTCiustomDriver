package com.ibs.ibs_antdrivers.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ibs.ibs_antdrivers.cache.entities.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {

    @Query("SELECT * FROM stores ORDER BY storeName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("""
        SELECT * FROM stores
        WHERE (instr(lower(storeName), lower(:query)) > 0 OR instr(lower(storeId), lower(:query)) > 0)
          AND (:franchise IS NULL OR storeFranchise = :franchise)
          AND (:region IS NULL OR storeRegion = :region)
        ORDER BY storeName COLLATE NOCASE ASC, storeId COLLATE NOCASE ASC
        LIMIT :limit
    """)
    fun observePage(query: String, franchise: String?, region: String?, limit: Int): Flow<List<StoreEntity>>

    @Query("""
        SELECT COUNT(*) FROM stores
        WHERE (instr(lower(storeName), lower(:query)) > 0 OR instr(lower(storeId), lower(:query)) > 0)
          AND (:franchise IS NULL OR storeFranchise = :franchise)
          AND (:region IS NULL OR storeRegion = :region)
    """)
    fun observeMatchCount(query: String, franchise: String?, region: String?): Flow<Int>

    @Query("SELECT DISTINCT storeFranchise FROM stores WHERE trim(storeFranchise) != '' ORDER BY storeFranchise COLLATE NOCASE")
    fun observeFranchises(): Flow<List<String>>

    @Query("SELECT DISTINCT storeRegion FROM stores WHERE trim(storeRegion) != '' ORDER BY storeRegion COLLATE NOCASE")
    fun observeRegions(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM stores")
    suspend fun count(): Int

    @Query("SELECT * FROM stores WHERE storeId = :storeId LIMIT 1")
    suspend fun getById(storeId: String): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StoreEntity>)

    @Query("DELETE FROM stores")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<StoreEntity>) {
        deleteAll()
        upsertAll(items)
    }
}

