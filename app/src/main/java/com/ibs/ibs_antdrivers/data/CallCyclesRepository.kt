package com.ibs.ibs_antdrivers.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Reads calling cycle data for the logged-in driver.
 *
 * RTDB structure (based on your sample):
 * - callingCycles/activeWeek/{driverUid}/{weekKey}/active=true
 * - callingCycles/weeks/{driverUid}/{weekKey}
 * - callingCycles/templates/{driverUid}/{templateId} (one of them can be isActive=true)
 * - callingCycles/spontaneous/{driverUid}/{yyyy-MM-dd}/{callId}
 */
class CallCyclesRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {

    fun uid(): String? = auth.currentUser?.uid

    data class WeekDay(
        val dayOfWeek: Int? = null,
        val storeIds: List<String>? = null,
    )

    data class Week(
        val weekKey: String? = null,
        val weekStart: String? = null,
        val weekEnd: String? = null,
        val days: List<WeekDay>? = null,
        val createdAt: Long? = null,
    )

    data class Template(
        val templateId: String? = null,
        val templateName: String? = null,
        val isActive: Boolean? = null,
        val days: List<WeekDay>? = null,
        val createdAt: Long? = null,
    )

    data class DisplayCycle(
        val title: String,
        val source: String, // TEMPLATE or WEEK
        val week: Week? = null,
        val template: Template? = null,
    )

    data class SpontaneousCall(
        val callId: String? = null,
        val createdAt: Long? = null,
        val date: String? = null, // yyyy-MM-dd
        val dayOfWeek: Int? = null,
        val driverUid: String? = null,
        val storeId: String? = null,
        val type: String? = null,
    )

    companion object {
        const val WEEKLY_CYCLE_LABEL = "Weekly Cycle"
    }

    /** Returns something like "2026-W02" or null if none active. */
    suspend fun getActiveWeekKey(driverUid: String): String? {
        val snap = db.child("callingCycles").child("activeWeek").child(driverUid).get().await()
        if (!snap.exists()) return null

        // Find the child with active==true; if multiple, pick most recently updated.
        val activeCandidates = snap.children.mapNotNull { weekNode ->
            val wk = weekNode.key ?: return@mapNotNull null
            val active = weekNode.child("active").getValue(Boolean::class.java) ?: false
            if (!active) return@mapNotNull null
            val updatedAt = weekNode.child("updatedAt").asLong() ?: 0L
            Triple(wk, updatedAt, true)
        }

        return activeCandidates.maxByOrNull { it.second }?.first
    }

    suspend fun getWeek(driverUid: String, weekKey: String): Week? {
        val snap = db.child("callingCycles").child("weeks").child(driverUid).child(weekKey).get().await()
        if (!snap.exists()) return null
        return snap.toWeek().copy(weekKey = weekKey)
    }

    suspend fun getAvailableWeeks(driverUid: String): List<String> {
        val snap = db.child("callingCycles").child("weeks").child(driverUid).get().await()
        if (!snap.exists()) return emptyList()
        return snap.children.mapNotNull { it.key }.sortedDescending()
    }

    /** Returns the currently active weekly template for the driver (isActive=true). */
    suspend fun getActiveTemplate(driverUid: String): Template? {
        val snap = db.child("callingCycles").child("templates").child(driverUid).get().await()
        if (!snap.exists()) return null

        // Find isActive==true; if multiple, pick most recently created.
        val candidates = snap.children.mapNotNull { tmplNode ->
            val id = tmplNode.key ?: return@mapNotNull null
            val isActive = tmplNode.child("isActive").getValue(Boolean::class.java) ?: false
            if (!isActive) return@mapNotNull null
            val createdAt = tmplNode.child("createdAt").asLong() ?: 0L
            Pair(id, createdAt)
        }

        val chosen = candidates.maxByOrNull { it.second }?.first ?: return null
        val chosenSnap = snap.child(chosen)
        return chosenSnap.toTemplate().copy(templateId = chosen)
    }

    /**
     * Resolve what to display for planned calls.
     * - If selection is [WEEKLY_CYCLE_LABEL], return active template.
     * - Else, try week override for that weekKey; if not found, fall back to active template.
     */
    suspend fun resolvePlannedCycle(driverUid: String, selection: String?): DisplayCycle? {
        if (selection.isNullOrBlank() || selection == WEEKLY_CYCLE_LABEL) {
            val tmpl = getActiveTemplate(driverUid) ?: return null
            return DisplayCycle(title = tmpl.templateName?.ifBlank { WEEKLY_CYCLE_LABEL } ?: WEEKLY_CYCLE_LABEL, source = "TEMPLATE", template = tmpl)
        }

        val week = getWeek(driverUid, selection)
        if (week != null) {
            return DisplayCycle(title = selection, source = "WEEK", week = week)
        }

        val tmpl = getActiveTemplate(driverUid) ?: return null
        return DisplayCycle(title = tmpl.templateName?.ifBlank { WEEKLY_CYCLE_LABEL } ?: WEEKLY_CYCLE_LABEL, source = "TEMPLATE", template = tmpl)
    }

    /**
     * Returns spontaneous calls for the provided date node (yyyy-MM-dd).
     * RTDB: callingCycles/spontaneous/{driverUid}/{yyyy-MM-dd}/{callId}
     */
    suspend fun getSpontaneousCallsForDate(driverUid: String, dateKey: String): List<SpontaneousCall> {
        val snap = db.child("callingCycles").child("spontaneous").child(driverUid).child(dateKey).get().await()
        if (!snap.exists()) return emptyList()

        val out = snap.children.mapNotNull { callNode ->
            callNode.toSpontaneousCall(fallbackDate = dateKey)
        }

        return out.sortedByDescending { it.createdAt ?: 0L }
    }

    /**
     * Returns spontaneous calls across all dates (older behavior). Prefer [getSpontaneousCallsForDate].
     */
    suspend fun getSpontaneousCalls(driverUid: String): List<SpontaneousCall> {
        val snap = db.child("callingCycles").child("spontaneous").child(driverUid).get().await()
        if (!snap.exists()) return emptyList()

        val out = mutableListOf<SpontaneousCall>()
        for (dateNode in snap.children) {
            val dateKey = dateNode.key
            for (callNode in dateNode.children) {
                out += callNode.toSpontaneousCall(fallbackDate = dateKey) ?: continue
            }
        }
        return out.sortedByDescending { it.createdAt ?: 0L }
    }

    private fun DataSnapshot.toWeek(): Week {
        val weekStart = child("weekStart").getValue(String::class.java)
        val weekEnd = child("weekEnd").getValue(String::class.java)
        val createdAt = child("createdAt").asLong()

        val daysNode = child("days")
        val days = if (daysNode.exists()) {
            daysNode.children.mapNotNull { d ->
                val dow = d.child("dayOfWeek").asInt()
                val storeIds = d.child("storeIds").children.mapNotNull { it.getValue(String::class.java) }
                WeekDay(dayOfWeek = dow, storeIds = storeIds)
            }
        } else null

        return Week(
            weekStart = weekStart,
            weekEnd = weekEnd,
            days = days,
            createdAt = createdAt,
        )
    }

    private fun DataSnapshot.toTemplate(): Template {
        val templateName = child("templateName").getValue(String::class.java)
        val isActive = child("isActive").getValue(Boolean::class.java) ?: false
        val createdAt = child("createdAt").asLong()

        val daysNode = child("days")
        val days = if (daysNode.exists()) {
            daysNode.children.mapNotNull { d ->
                val dow = d.child("dayOfWeek").asInt()
                val storeIds = d.child("storeIds").children.mapNotNull { it.getValue(String::class.java) }
                WeekDay(dayOfWeek = dow, storeIds = storeIds)
            }
        } else null

        return Template(
            templateName = templateName,
            isActive = isActive,
            createdAt = createdAt,
            days = days,
        )
    }

    private fun DataSnapshot.toSpontaneousCall(fallbackDate: String?): SpontaneousCall? {
        // NOTE: Using manual parsing to avoid RTDB type mismatch exceptions.
        val callId = child("callId").getValue(String::class.java) ?: key
        if (callId.isNullOrBlank()) return null

        val createdAt = child("createdAt").asLong()
        val date = child("date").getValue(String::class.java) ?: fallbackDate
        val dayOfWeek = child("dayOfWeek").asInt()
        val driverUid = child("driverUid").getValue(String::class.java)
        val storeId = child("storeId").getValue(String::class.java)
        val type = child("type").getValue(String::class.java)

        return SpontaneousCall(
            callId = callId,
            createdAt = createdAt,
            date = date,
            dayOfWeek = dayOfWeek,
            driverUid = driverUid,
            storeId = storeId,
            type = type,
        )
    }

    private fun DataSnapshot.asLong(): Long? {
        val v = value ?: return null
        return when (v) {
            is Long -> v
            is Int -> v.toLong()
            is Double -> v.toLong()
            is Float -> v.toLong()
            is String -> v.toLongOrNull()
            is Number -> v.toLong()
            else -> null
        }
    }

    private fun DataSnapshot.asInt(): Int? {
        val v = value ?: return null
        return when (v) {
            is Int -> v
            is Long -> v.toInt()
            is Double -> v.toInt()
            is Float -> v.toInt()
            is String -> v.toIntOrNull()
            is Number -> v.toInt()
            else -> null
        }
    }
}
