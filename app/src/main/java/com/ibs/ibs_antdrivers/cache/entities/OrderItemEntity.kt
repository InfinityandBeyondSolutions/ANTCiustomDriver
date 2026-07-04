package com.ibs.ibs_antdrivers.cache.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "order_items",
    primaryKeys = ["orderId", "itemId"],
    indices = [Index(value = ["orderId"])]
)
data class OrderItemEntity(
    val orderId: String,
    val itemId: String,
    val productId: String,
    val productCode: String,
    val productName: String,
    val brand: String,
    val size: String,
    val unitBarcode: String,
    val outerBarcode: String,
    val unitPriceExVat: Double,
    val casePriceExVat: Double,
    val quantity: Int,
    /** Number of individual units ordered (separate from case quantity). */
    val unitQuantity: Int = 0,
    val totalPrice: Double,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

