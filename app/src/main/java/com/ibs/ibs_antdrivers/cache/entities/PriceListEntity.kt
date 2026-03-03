package com.ibs.ibs_antdrivers.cache.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_lists",
    indices = [Index(value = ["title"]), Index(value = ["name"])]
)
data class PriceListEntity(
    @PrimaryKey
    val priceListId: String,
    val title: String,
    val name: String,
    val companyName: String,
    val effectiveDate: String,
    val status: String,
    val includeVat: Boolean,
    val updatedAt: String,
    val createdAt: String,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

