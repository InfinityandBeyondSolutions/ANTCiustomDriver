package com.ibs.ibs_antdrivers.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ibs.ibs_antdrivers.cache.entities.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {

    @Query("SELECT * FROM stores ORDER BY storeName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE storeId = :storeId LIMIT 1")
    suspend fun getById(storeId: String): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StoreEntity>)

    @Query("DELETE FROM stores")
    suspend fun deleteAll()
}

