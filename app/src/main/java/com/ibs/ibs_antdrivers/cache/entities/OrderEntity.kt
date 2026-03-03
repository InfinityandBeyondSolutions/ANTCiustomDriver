package com.ibs.ibs_antdrivers.cache.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "orders",
    indices = [Index(value = ["driverId"]), Index(value = ["createdAt"])]
)
data class OrderEntity(
    @PrimaryKey
    val orderId: String,
    val orderNumber: String,
    val notes: String,
    val storeId: String,
    val storeName: String,
    val priceListId: String,
    val priceListName: String,
    val driverId: String,
    val driverName: String,
    val createdByUserId: String,
    val createdByUserName: String,
    val createdByFirstName: String,
    val createdByLastName: String,
    val completedByUserId: String,
    val completedByUserName: String,
    val completedByFirstName: String,
    val completedByLastName: String,
    val priority: String,
    val status: String,
    val totalAmount: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

