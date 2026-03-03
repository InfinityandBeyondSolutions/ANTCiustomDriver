package com.ibs.ibs_antdrivers.cache.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalogue_categories",
    indices = [Index(value = ["isActive"])],
)
data class CatalogueCategoryEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val isActive: Boolean,
    val fileName: String?,
    val fileSizeBytes: Long?,
    val fileUrl: String?,
    val uploadedAtUnixMs: Long?,
    val uploadedBy: String?,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

