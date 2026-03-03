package com.ibs.ibs_antdrivers.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.rtdbqueue.RtdbPath
import com.ibs.ibs_antdrivers.rtdbqueue.RtdbWriteQueue
import kotlinx.coroutines.tasks.await

/**
 * Persists the "visited" (checkbox) state for today's planned and spontaneous stores.
 *
 * RTDB structure:
 * callingCycles/visited/{driverUid}/{yyyy-MM-dd}/{storeOrCallId}
 *   - visited: Boolean
 *   - updatedAt: Long (epoch millis)
 *
 * Keys starting with "sc_" are spontaneous call IDs; all others are planned store IDs.
 */
class VisitedStoresRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {

    fun uid(): String? = auth.currentUser?.uid

    /** Returns the set of IDs that are marked visited for [driverUid] on [dateKey] (yyyy-MM-dd). */
    suspend fun getVisitedIds(driverUid: String, dateKey: String): Set<String> {
        val snap = db.child("callingCycles")
            .child("visited")
            .child(driverUid)
            .child(dateKey)
            .get()
            .await()

        if (!snap.exists()) return emptySet()

        return snap.children.mapNotNull { node ->
            val id = node.key ?: return@mapNotNull null
            val visited = node.child("visited").getValue(Boolean::class.java) ?: false
            if (visited) id else null
        }.toSet()
    }

    /** Marks or unmarks a single [id] as visited for today on Firebase. */
    suspend fun setVisited(driverUid: String, dateKey: String, id: String, visited: Boolean, appContext: Context? = null) {
        val ref = db.child("callingCycles")
            .child("visited")
            .child(driverUid)
            .child(dateKey)
            .child(id)

        val path = RtdbPath.child("callingCycles", "visited", driverUid, dateKey, id)
        val dedupeKey = "visited:$driverUid:$dateKey:$id"

        try {
            if (visited) {
                ref.setValue(
                    mapOf(
                        "visited" to true,
                        "updatedAt" to System.currentTimeMillis(),
                    )
                ).await()
            } else {
                ref.removeValue().await()
            }
        } catch (t: Throwable) {
            val ctx = appContext?.applicationContext
            if (ctx != null) {
                if (visited) {
                    RtdbWriteQueue.enqueueSetMap(
                        context = ctx,
                        path = path,
                        value = mapOf("visited" to true, "updatedAt" to System.currentTimeMillis()),
                        priority = 1,
                        dedupeKey = dedupeKey
                    )
                } else {
                    RtdbWriteQueue.enqueueDelete(
                        context = ctx,
                        path = path,
                        priority = 1,
                        dedupeKey = dedupeKey
                    )
                }
            } else {
                throw t
            }
        }
    }
}
