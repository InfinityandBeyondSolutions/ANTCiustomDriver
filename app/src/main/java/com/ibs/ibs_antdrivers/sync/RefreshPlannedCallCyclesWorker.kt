package com.ibs.ibs_antdrivers.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.cache.entities.CcActiveWeekEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTemplateDayStoreEntity
import com.ibs.ibs_antdrivers.cache.entities.CcTemplateEntity
import com.ibs.ibs_antdrivers.cache.entities.CcWeekDayStoreEntity
import com.ibs.ibs_antdrivers.cache.entities.CcWeekEntity
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.tasks.await

/**
 * Refreshes planned calling cycles (weeks + templates + activeWeek) into Room.
 * This powers the Planned Week tab fully offline.
 */
class RefreshPlannedCallCyclesWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        return try {
            val dbRef = FirebaseDatabase.getInstance().reference.child("callingCycles")
            val dao = AppDatabase.get(applicationContext).callCyclesDao()

            // activeWeek
            val activeSnap = dbRef.child("activeWeek").child(uid).get().await()
            val activeEntities = activeSnap.children.mapNotNull { node ->
                val weekKey = node.key ?: return@mapNotNull null
                val active = node.child("active").getValue(Boolean::class.java) ?: false
                val updatedAt = node.child("updatedAt").getValue(Long::class.java) ?: 0L
                CcActiveWeekEntity(driverUid = uid, weekKey = weekKey, active = active, updatedAt = updatedAt)
            }
            dao.deleteActiveWeeksForDriver(uid)
            if (activeEntities.isNotEmpty()) dao.upsertActiveWeeks(activeEntities)

            // templates
            val templatesSnap = dbRef.child("templates").child(uid).get().await()
            val templates = mutableListOf<CcTemplateEntity>()
            val templateDayStores = mutableListOf<CcTemplateDayStoreEntity>()
            if (templatesSnap.exists()) {
                for (tmplNode in templatesSnap.children) {
                    val templateId = tmplNode.key ?: continue
                    templates += CcTemplateEntity(
                        driverUid = uid,
                        templateId = templateId,
                        templateName = tmplNode.child("templateName").getValue(String::class.java) ?: "",
                        isActive = tmplNode.child("isActive").getValue(Boolean::class.java) ?: false,
                        createdAt = tmplNode.child("createdAt").getValue(Long::class.java) ?: 0L,
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
            dao.deleteTemplatesForDriver(uid)
            dao.deleteTemplateDaysForDriver(uid)
            dao.deleteTemplateDayStoresForDriver(uid)
            if (templates.isNotEmpty()) dao.upsertTemplates(templates)
            if (templateDayStores.isNotEmpty()) dao.upsertTemplateDayStores(templateDayStores)

            // weeks
            val weeksSnap = dbRef.child("weeks").child(uid).get().await()
            val weeks = mutableListOf<CcWeekEntity>()
            val weekDayStores = mutableListOf<CcWeekDayStoreEntity>()
            if (weeksSnap.exists()) {
                for (weekNode in weeksSnap.children) {
                    val weekKey = weekNode.key ?: continue
                    weeks += CcWeekEntity(
                        driverUid = uid,
                        weekKey = weekKey,
                        weekStart = weekNode.child("weekStart").getValue(String::class.java) ?: "",
                        weekEnd = weekNode.child("weekEnd").getValue(String::class.java) ?: "",
                        createdAt = weekNode.child("createdAt").getValue(Long::class.java) ?: 0L,
                    )

                    val daysNode = weekNode.child("days")
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
                }
            }
            dao.deleteWeeksForDriver(uid)
            dao.deleteWeekDaysForDriver(uid)
            dao.deleteWeekDayStoresForDriver(uid)
            if (weeks.isNotEmpty()) dao.upsertWeeks(weeks)
            if (weekDayStores.isNotEmpty()) dao.upsertWeekDayStores(weekDayStores)

            Result.success()
        } catch (t: Throwable) {
            Log.w("RefreshPlannedCallCycles", "Failed: ${t.message}")
            Result.retry()
        }
    }
}

