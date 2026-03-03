package com.ibs.ibs_antdrivers.cache.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stores",
    indices = [
        Index(value = ["storeName"]),
        Index(value = ["storeRegion"]),
        Index(value = ["storeFranchise"])
    ]
)
data class StoreEntity(
    @PrimaryKey
    val storeId: String,
    val storeName: String,
    val storeRegion: String,
    val storeFranchise: String,
    val storeContactNum: String,
    val repName: String,
    val contactPerson: String,
    val storeEmail: String,
    val storeAddress: String,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

