package com.ibs.ibs_antdrivers.cache.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Precomputed "today planned stores" list for a driver.
 * This avoids flow-switching logic and guarantees Today tab works offline
 * even when there's no week override (template fallback).
 */
@Entity(
    tableName = "cc_today_planned_stores",
    primaryKeys = ["driverUid", "dateKey", "storeId"],
    indices = [Index(value = ["driverUid", "dateKey"]), Index(value = ["storeId"])]
)
data class CcTodayPlannedStoreEntity(
    val driverUid: String,
    val dateKey: String, // yyyy-MM-dd
    val storeId: String,
)

