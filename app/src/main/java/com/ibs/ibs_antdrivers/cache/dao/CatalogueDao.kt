package com.ibs.ibs_antdrivers.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ibs.ibs_antdrivers.cache.entities.CatalogueCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogueDao {

    @Query("SELECT * FROM catalogue_categories WHERE isActive = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveCategories(): Flow<List<CatalogueCategoryEntity>>

    @Query("SELECT * FROM catalogue_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): CatalogueCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CatalogueCategoryEntity>)

    @Query("DELETE FROM catalogue_categories")
    suspend fun deleteAll()
}

