package com.ibs.ibs_antdrivers.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.rtdbqueue.RtdbPath
import com.ibs.ibs_antdrivers.rtdbqueue.RtdbWriteQueue
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Repository for managing completed store calls.
 *
 * RTDB structure:
 * completedCalls/{driverUid}/{yyyy-MM-dd}/{callId}
 *   - storeId: String
 *   - storeName: String?
 *   - callType: String ("planned" | "spontaneous")
 *   - startTime: Long (timestamp)
 *   - endTime: Long? (timestamp)
 *   - date: String (yyyy-MM-dd)
 *   - driverUid: String
 */
class CompletedCallsRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {

    fun uid(): String? = auth.currentUser?.uid

    data class CompletedCall(
        val callId: String? = null,
        val storeId: String? = null,
        val storeName: String? = null,
        val callType: String? = null, // "planned" or "spontaneous"
        val startTime: Long? = null,
        val endTime: Long? = null,
        val date: String? = null,
        val driverUid: String? = null,
    )

    data class ActiveCall(
        val callId: String,
        val storeId: String,
        val storeName: String?,
        val callType: String,
        val startTime: Long,
        val date: String,
    )

    /**
     * Start a call for a store. Creates a record with startTime.
     */
    suspend fun startCall(
        storeId: String,
        storeName: String?,
        callType: String = "planned",
        appContext: Context? = null
    ): String {
        val uid = uid() ?: throw IllegalStateException("Not signed in")
        val now = System.currentTimeMillis()
        val dateKey = LocalDate.now().toString() // yyyy-MM-dd

        val callId = db.child("completedCalls")
            .child(uid)
            .child(dateKey)
            .push()
            .key ?: throw IllegalStateException("Failed to generate call ID")

        val callData = mapOf(
            "callId" to callId,
            "storeId" to storeId,
            "storeName" to storeName,
            "callType" to callType,
            "startTime" to now,
            "date" to dateKey,
            "driverUid" to uid,
        )

        val path = RtdbPath.child("completedCalls", uid, dateKey, callId)

        try {
            db.child("completedCalls")
                .child(uid)
                .child(dateKey)
                .child(callId)
                .setValue(callData)
                .await()
        } catch (t: Throwable) {
            val ctx = appContext?.applicationContext
            if (ctx != null) {
                RtdbWriteQueue.enqueueSetMap(
                    context = ctx,
                    path = path,
                    value = callData,
                    priority = 1,
                    dedupeKey = "callStart:$uid:$dateKey:$callId"
                )
            } else {
                throw t
            }
        }

        return callId
    }

    /**
     * End a call by setting the endTime.
     */
    suspend fun endCall(callId: String, dateKey: String, appContext: Context? = null) {
        val uid = uid() ?: throw IllegalStateException("Not signed in")
        val now = System.currentTimeMillis()

        val path = RtdbPath.child("completedCalls", uid, dateKey, callId)

        try {
            db.child("completedCalls")
                .child(uid)
                .child(dateKey)
                .child(callId)
                .child("endTime")
                .setValue(now)
                .await()
        } catch (t: Throwable) {
            val ctx = appContext?.applicationContext
            if (ctx != null) {
                RtdbWriteQueue.enqueueUpdateMap(
                    context = ctx,
                    path = path,
                    updates = mapOf("endTime" to now),
                    priority = 1,
                    dedupeKey = "callEnd:$uid:$dateKey:$callId"
                )
            } else {
                throw t
            }
        }
    }

    /**
     * Get active call (one that has startTime but no endTime) for a specific store on today's date.
     */
    suspend fun getActiveCallForStore(storeId: String): ActiveCall? {
        val uid = uid() ?: return null
        val dateKey = LocalDate.now().toString()

        val snap = db.child("completedCalls")
            .child(uid)
            .child(dateKey)
            .get()
            .await()

        if (!snap.exists()) return null

        for (callNode in snap.children) {
            val call = callNode.toCompletedCall()
            if (call?.storeId == storeId && call.startTime != null && call.endTime == null) {
                return ActiveCall(
                    callId = call.callId ?: continue,
                    storeId = call.storeId,
                    storeName = call.storeName,
                    callType = call.callType ?: "planned",
                    startTime = call.startTime,
                    date = call.date ?: dateKey,
                )
            }
        }

        return null
    }

    /**
     * Get all completed calls for a specific date.
     */
    suspend fun getCompletedCallsForDate(dateKey: String): List<CompletedCall> {
        val uid = uid() ?: return emptyList()

        val snap = db.child("completedCalls")
            .child(uid)
            .child(dateKey)
            .get()
            .await()

        if (!snap.exists()) return emptyList()

        return snap.children.mapNotNull { it.toCompletedCall() }
            .sortedByDescending { it.startTime }
    }

    /**
     * Check if a call is active (has startTime but no endTime) for a store today.
     */
    suspend fun isCallActiveForStore(storeId: String): Boolean {
        return getActiveCallForStore(storeId) != null
    }

    private fun DataSnapshot.toCompletedCall(): CompletedCall? {
        val callId = child("callId").getValue(String::class.java) ?: key ?: return null

        return CompletedCall(
            callId = callId,
            storeId = child("storeId").getValue(String::class.java),
            storeName = child("storeName").getValue(String::class.java),
            callType = child("callType").getValue(String::class.java),
            startTime = child("startTime").getValue(Long::class.java),
            endTime = child("endTime").getValue(Long::class.java),
            date = child("date").getValue(String::class.java),
            driverUid = child("driverUid").getValue(String::class.java),
        )
    }
}

