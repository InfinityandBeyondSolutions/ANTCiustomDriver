package com.ibs.ibs_antdrivers.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.cache.entities.CcSpontaneousCallEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTemplateDayStoreEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTemplateEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTodayPlannedStoreEntity
import com.ibs.ibs_antdrivers.cache.entities.CcVisitedEntity
import com.ibs.ibs_antdrivers.cache.entities.CcWeekDayStoreEntity
import com.ibs.ibs_antdrivers.cache.entities.CcWeekEntity
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Today-first calling cycle refresh.
 *
 * Populates enough data for the Today tab to work fully offline:
 * - weekly template (active)
 * - current week override (if exists)
 * - spontaneous calls for today
 * - visited state for today
 */
class RefreshTodayCallCycleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val todayKey = LocalDate.now().toString()
        val todayDow = LocalDate.now().dayOfWeek.value

        return try {
            val dbRef = FirebaseDatabase.getInstance().reference
            val cache = AppDatabase.get(applicationContext).callCyclesDao()

            // 1) Active template
            val templatesSnap = dbRef.child("callingCycles").child("templates").child(uid).get().await()
            val templates = mutableListOf<CcTemplateEntity>()
            val templateDayStores = mutableListOf<CcTemplateDayStoreEntity>()

            if (templatesSnap.exists()) {
                for (tmplNode in templatesSnap.children) {
                    val templateId = tmplNode.key ?: continue
                    val templateName = tmplNode.child("templateName").getValue(String::class.java) ?: ""
                    val isActive = tmplNode.child("isActive").getValue(Boolean::class.java) ?: false
                    val createdAt = tmplNode.child("createdAt").getValue(Long::class.java) ?: 0L

                    templates += CcTemplateEntity(
                        driverUid = uid,
                        templateId = templateId,
                        templateName = templateName,
                        isActive = isActive,
                        createdAt = createdAt,
                    )

                    val daysNode = tmplNode.child("days")
                    if (daysNode.exists()) {
                        for (d in daysNode.children) {
                            val dow = d.child("dayOfWeek").getValue(Int::class.java) ?: continue
                            val storeIds = d.child("storeIds").children.mapNotNull { it.getValue(String::class.java) }
                            storeIds.forEach { storeId ->
                                templateDayStores += CcTemplateDayStoreEntity(
                                    driverUid = uid,
                                    templateId = templateId,
                                    dayOfWeek = dow,
                                    storeId = storeId,
                                )
                            }
                        }
                    }
                }
            }

            cache.deleteTemplatesForDriver(uid)
            cache.deleteTemplateDaysForDriver(uid)
            cache.deleteTemplateDayStoresForDriver(uid)
            cache.upsertTemplates(templates)
            cache.upsertTemplateDayStores(templateDayStores)

            // 2) Week override for current week key (if present)
            val weekKey = currentWeekKey(LocalDate.now())
            val weekSnap = dbRef.child("callingCycles").child("weeks").child(uid).child(weekKey).get().await()

            cache.deleteWeeksForDriver(uid)
            cache.deleteWeekDaysForDriver(uid)
            cache.deleteWeekDayStoresForDriver(uid)

            if (weekSnap.exists()) {
                val weekStart = weekSnap.child("weekStart").getValue(String::class.java) ?: ""
                val weekEnd = weekSnap.child("weekEnd").getValue(String::class.java) ?: ""
                val createdAt = weekSnap.child("createdAt").getValue(Long::class.java) ?: 0L

                cache.upsertWeeks(
                    listOf(
                        CcWeekEntity(
                            driverUid = uid,
                            weekKey = weekKey,
                            weekStart = weekStart,
                            weekEnd = weekEnd,
                            createdAt = createdAt,
                        )
                    )
                )

                val weekDayStores = mutableListOf<CcWeekDayStoreEntity>()
                val daysNode = weekSnap.child("days")
                if (daysNode.exists()) {
                    for (d in daysNode.children) {
                        val dow = d.child("dayOfWeek").getValue(Int::class.java) ?: continue
                        val storeIds = d.child("storeIds").children.mapNotNull { it.getValue(String::class.java) }
                        storeIds.forEach { storeId ->
                            weekDayStores += CcWeekDayStoreEntity(
                                driverUid = uid,
                                weekKey = weekKey,
                                dayOfWeek = dow,
                                storeId = storeId,
                            )
                        }
                    }
                }
                cache.upsertWeekDayStores(weekDayStores)
            }

            // Precompute today's planned stores:
            // prefer week override for current week if present, else active template.
            val plannedToday = mutableSetOf<String>()

            // From week override
            if (weekSnap.exists()) {
                val daysNode = weekSnap.child("days")
                if (daysNode.exists()) {
                    for (d in daysNode.children) {
                        val dow = d.child("dayOfWeek").getValue(Int::class.java) ?: continue
                        if (dow != todayDow) continue
                        plannedToday += d.child("storeIds").children.mapNotNull { it.getValue(String::class.java) }
                    }
                }
            }

            // From active template (fallback)
            if (plannedToday.isEmpty()) {
                val activeTemplateNode = templatesSnap.children.firstOrNull { node ->
                    node.child("isActive").getValue(Boolean::class.java) == true
                }
                if (activeTemplateNode != null) {
                    val daysNode = activeTemplateNode.child("days")
                    if (daysNode.exists()) {
                        for (d in daysNode.children) {
                            val dow = d.child("dayOfWeek").getValue(Int::class.java) ?: continue
                            if (dow != todayDow) continue
                            plannedToday += d.child("storeIds").children.mapNotNull { it.getValue(String::class.java) }
                        }
                    }
                }
            }

            cache.deleteTodayPlannedForDate(uid, todayKey)
            cache.upsertTodayPlannedStores(
                plannedToday.map { storeId ->
                    CcTodayPlannedStoreEntity(
                        driverUid = uid,
                        dateKey = todayKey,
                        storeId = storeId,
                    )
                }
            )

            // 3) Spontaneous calls for today
            val spontSnap = dbRef.child("callingCycles").child("spontaneous").child(uid).child(todayKey).get().await()
            val spont = mutableListOf<CcSpontaneousCallEntity>()
            if (spontSnap.exists()) {
                for (callNode in spontSnap.children) {
                    val callId = callNode.child("callId").getValue(String::class.java) ?: callNode.key ?: continue
                    val storeId = callNode.child("storeId").getValue(String::class.java) ?: continue
                    spont += CcSpontaneousCallEntity(
                        driverUid = uid,
                        dateKey = todayKey,
                        callId = callId,
                        storeId = storeId,
                        createdAt = callNode.child("createdAt").getValue(Long::class.java) ?: 0L,
                        dayOfWeek = callNode.child("dayOfWeek").getValue(Int::class.java) ?: 0,
                        type = callNode.child("type").getValue(String::class.java) ?: "spontaneous",
                    )
                }
            }
            cache.deleteSpontaneousForDate(uid, todayKey)
            cache.upsertSpontaneousCalls(spont)

            // 4) Visited state for today
            val visitedSnap = dbRef.child("callingCycles").child("visited").child(uid).child(todayKey).get().await()
            val visited = mutableListOf<CcVisitedEntity>()
            if (visitedSnap.exists()) {
                for (node in visitedSnap.children) {
                    val id = node.key ?: continue
                    val v = node.child("visited").getValue(Boolean::class.java) ?: false
                    if (!v) continue
                    visited += CcVisitedEntity(
                        driverUid = uid,
                        dateKey = todayKey,
                        id = id,
                        visited = true,
                        updatedAt = node.child("updatedAt").getValue(Long::class.java) ?: 0L,
                    )
                }
            }
            cache.deleteVisitedForDate(uid, todayKey)
            cache.upsertVisited(visited)

            Result.success()
        } catch (t: Throwable) {
            Log.w("RefreshTodayCallCycle", "Failed: ${t.message}")
            Result.retry()
        }
    }

    private fun currentWeekKey(date: LocalDate): String {
        val wf = java.time.temporal.WeekFields.ISO
        val week = date.get(wf.weekOfWeekBasedYear())
        val year = date.get(wf.weekBasedYear())
        return String.format(java.util.Locale.US, "%d-W%02d", year, week)
    }
}
