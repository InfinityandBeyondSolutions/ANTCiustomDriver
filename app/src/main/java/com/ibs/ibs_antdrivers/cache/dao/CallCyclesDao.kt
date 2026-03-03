package com.ibs.ibs_antdrivers.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ibs.ibs_antdrivers.cache.entities.CcActiveWeekEntity
import com.ibs.ibs_antdrivers.cache.entities.CcSpontaneousCallEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTemplateDayStoreEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTemplateEntity
import com.ibs.ibs_antdrivers.cache.entities.CcVisitedEntity
import com.ibs.ibs_antdrivers.cache.entities.CcWeekDayStoreEntity
import com.ibs.ibs_antdrivers.cache.entities.CcWeekEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTodayPlannedStoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallCyclesDao {

    @Query("SELECT weekKey FROM cc_weeks WHERE driverUid = :driverUid ORDER BY weekKey DESC")
    fun observeWeekKeys(driverUid: String): Flow<List<String>>

    @Query("SELECT * FROM cc_templates WHERE driverUid = :driverUid AND isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveTemplate(driverUid: String): Flow<CcTemplateEntity?>

    @Query("SELECT storeId FROM cc_template_day_stores WHERE driverUid = :driverUid AND templateId = :templateId AND dayOfWeek = :dayOfWeek")
    fun observeTemplateStoreIdsForDay(driverUid: String, templateId: String, dayOfWeek: Int): Flow<List<String>>

    @Query("SELECT * FROM cc_weeks WHERE driverUid = :driverUid AND weekKey = :weekKey LIMIT 1")
    fun observeWeek(driverUid: String, weekKey: String): Flow<CcWeekEntity?>

    @Query("SELECT storeId FROM cc_week_day_stores WHERE driverUid = :driverUid AND weekKey = :weekKey AND dayOfWeek = :dayOfWeek")
    fun observeWeekStoreIdsForDay(driverUid: String, weekKey: String, dayOfWeek: Int): Flow<List<String>>

    @Query("SELECT * FROM cc_spontaneous_calls WHERE driverUid = :driverUid AND dateKey = :dateKey ORDER BY createdAt DESC")
    fun observeSpontaneousCalls(driverUid: String, dateKey: String): Flow<List<CcSpontaneousCallEntity>>

    @Query("SELECT id FROM cc_visited WHERE driverUid = :driverUid AND dateKey = :dateKey AND visited = 1")
    fun observeVisitedIds(driverUid: String, dateKey: String): Flow<List<String>>

    @Query("SELECT storeId FROM cc_today_planned_stores WHERE driverUid = :driverUid AND dateKey = :dateKey")
    fun observeTodayPlannedStoreIds(driverUid: String, dateKey: String): Flow<List<String>>

    // Upserts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeeks(items: List<CcWeekEntity>)

    @Query("DELETE FROM cc_weeks WHERE driverUid = :driverUid")
    suspend fun deleteWeeksForDriver(driverUid: String)

    @Query("DELETE FROM cc_week_days WHERE driverUid = :driverUid")
    suspend fun deleteWeekDaysForDriver(driverUid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeekDayStores(items: List<CcWeekDayStoreEntity>)

    @Query("DELETE FROM cc_week_day_stores WHERE driverUid = :driverUid")
    suspend fun deleteWeekDayStoresForDriver(driverUid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(items: List<CcTemplateEntity>)

    @Query("DELETE FROM cc_templates WHERE driverUid = :driverUid")
    suspend fun deleteTemplatesForDriver(driverUid: String)

    @Query("DELETE FROM cc_template_days WHERE driverUid = :driverUid")
    suspend fun deleteTemplateDaysForDriver(driverUid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplateDayStores(items: List<CcTemplateDayStoreEntity>)

    @Query("DELETE FROM cc_template_day_stores WHERE driverUid = :driverUid")
    suspend fun deleteTemplateDayStoresForDriver(driverUid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpontaneousCalls(items: List<CcSpontaneousCallEntity>)

    @Query("DELETE FROM cc_spontaneous_calls WHERE driverUid = :driverUid AND dateKey = :dateKey")
    suspend fun deleteSpontaneousForDate(driverUid: String, dateKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVisited(items: List<CcVisitedEntity>)

    @Query("DELETE FROM cc_visited WHERE driverUid = :driverUid AND dateKey = :dateKey")
    suspend fun deleteVisitedForDate(driverUid: String, dateKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActiveWeeks(items: List<CcActiveWeekEntity>)

    @Query("DELETE FROM cc_active_week WHERE driverUid = :driverUid")
    suspend fun deleteActiveWeeksForDriver(driverUid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTodayPlannedStores(items: List<CcTodayPlannedStoreEntity>)

    @Query("DELETE FROM cc_today_planned_stores WHERE driverUid = :driverUid AND dateKey = :dateKey")
    suspend fun deleteTodayPlannedForDate(driverUid: String, dateKey: String)
}
