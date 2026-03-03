package com.ibs.ibs_antdrivers.cache.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "price_list_items",
    primaryKeys = ["priceListId", "itemId"],
    indices = [Index(value = ["priceListId"]), Index(value = ["itemNo"])]
)
data class PriceListItemEntity(
    val priceListId: String,
    val itemId: String,
    val itemNo: String,
    val description: String,
    val brand: String,
    val size: String,
    val unitBarcode: String,
    val outerBarcode: String,
    val unitPrice: String,
    val casePrice: String,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

