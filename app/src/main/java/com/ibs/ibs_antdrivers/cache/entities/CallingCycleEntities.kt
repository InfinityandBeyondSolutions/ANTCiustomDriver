package com.ibs.ibs_antdrivers.cache.entities

import androidx.room.Entity
import androidx.room.Index

/** Active week selection for a driver (from callingCycles/activeWeek/{uid}). */
@Entity(
    tableName = "cc_active_week",
    primaryKeys = ["driverUid", "weekKey"],
    indices = [Index(value = ["driverUid"])]
)
data class CcActiveWeekEntity(
    val driverUid: String,
    val weekKey: String,
    val active: Boolean,
    val updatedAt: Long,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "cc_weeks",
    primaryKeys = ["driverUid", "weekKey"],
    indices = [Index(value = ["driverUid"])]
)
data class CcWeekEntity(
    val driverUid: String,
    val weekKey: String,
    val weekStart: String,
    val weekEnd: String,
    val createdAt: Long,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "cc_week_days",
    primaryKeys = ["driverUid", "weekKey", "dayOfWeek"],
    indices = [Index(value = ["driverUid", "weekKey"])]
)
data class CcWeekDayEntity(
    val driverUid: String,
    val weekKey: String,
    val dayOfWeek: Int,
)

@Entity(
    tableName = "cc_week_day_stores",
    primaryKeys = ["driverUid", "weekKey", "dayOfWeek", "storeId"],
    indices = [Index(value = ["driverUid", "weekKey", "dayOfWeek"]), Index(value = ["storeId"])]
)
data class CcWeekDayStoreEntity(
    val driverUid: String,
    val weekKey: String,
    val dayOfWeek: Int,
    val storeId: String,
)

@Entity(
    tableName = "cc_templates",
    primaryKeys = ["driverUid", "templateId"],
    indices = [Index(value = ["driverUid"])]
)
data class CcTemplateEntity(
    val driverUid: String,
    val templateId: String,
    val templateName: String,
    val isActive: Boolean,
    val createdAt: Long,
    val cachedAtMs: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "cc_template_days",
    primaryKeys = ["driverUid", "templateId", "dayOfWeek"],
    indices = [Index(value = ["driverUid", "templateId"])]
)
data class CcTemplateDayEntity(
    val driverUid: String,
    val templateId: String,
    val dayOfWeek: Int,
)

@Entity(
    tableName = "cc_template_day_stores",
    primaryKeys = ["driverUid", "templateId", "dayOfWeek", "storeId"],
    indices = [Index(value = ["driverUid", "templateId", "dayOfWeek"]), Index(value = ["storeId"])]
)
data class CcTemplateDayStoreEntity(
    val driverUid: String,
    val templateId: String,
    val dayOfWeek: Int,
    val storeId: String,
)

@Entity(
    tableName = "cc_spontaneous_calls",
    primaryKeys = ["driverUid", "dateKey", "callId"],
    indices = [Index(value = ["driverUid", "dateKey"]), Index(value = ["storeId"])]
)
data class CcSpontaneousCallEntity(
    val driverUid: String,
    val dateKey: String, // yyyy-MM-dd
    val callId: String,
    val storeId: String,
    val createdAt: Long,
    val dayOfWeek: Int,
    val type: String,
)

@Entity(
    tableName = "cc_visited",
    primaryKeys = ["driverUid", "dateKey", "id"],
    indices = [Index(value = ["driverUid", "dateKey"])]
)
data class CcVisitedEntity(
    val driverUid: String,
    val dateKey: String,
    val id: String,
    val visited: Boolean,
    val updatedAt: Long,
)

