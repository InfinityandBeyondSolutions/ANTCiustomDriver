package com.ibs.ibs_antdrivers.rtdbqueue

import android.content.Context
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase

/**
 * Minimal RTDB write queue.
 *
 * For now we queue values as strings (SET) or JSON objects (UPDATE). This is enough to ensure:
 * - app doesn't crash offline
 * - user can perform actions; they'll be retried once connected
 */
object RtdbWriteQueue {

    suspend fun enqueueSet(
        context: Context,
        path: String,
        valueAsString: String,
        priority: Int = 1,
        dedupeKey: String? = null
    ): Long {
        val dao = AppDatabase.get(context).pendingRtdbActionDao()
        val id = dao.insert(
            PendingRtdbAction(
                path = path,
                type = RtdbActionType.SET,
                payloadJson = valueAsString,
                priority = priority,
                dedupeKey = dedupeKey
            )
        )
        RtdbQueueWorkScheduler.enqueue(context)
        return id
    }

    suspend fun enqueueUpdate(
        context: Context,
        path: String,
        updateMapAsJsonObject: String,
        priority: Int = 1,
        dedupeKey: String? = null
    ): Long {
        val dao = AppDatabase.get(context).pendingRtdbActionDao()
        val id = dao.insert(
            PendingRtdbAction(
                path = path,
                type = RtdbActionType.UPDATE,
                payloadJson = updateMapAsJsonObject,
                priority = priority,
                dedupeKey = dedupeKey
            )
        )
        RtdbQueueWorkScheduler.enqueue(context)
        return id
    }

    suspend fun enqueueDelete(
        context: Context,
        path: String,
        priority: Int = 1,
        dedupeKey: String? = null
    ): Long {
        val dao = AppDatabase.get(context).pendingRtdbActionDao()
        val id = dao.insert(
            PendingRtdbAction(
                path = path,
                type = RtdbActionType.DELETE,
                payloadJson = null,
                priority = priority,
                dedupeKey = dedupeKey
            )
        )
        RtdbQueueWorkScheduler.enqueue(context)
        return id
    }

    suspend fun enqueueSetMap(
        context: Context,
        path: String,
        value: Map<String, Any?>,
        priority: Int = 1,
        dedupeKey: String? = null
    ): Long {
        val dao = AppDatabase.get(context).pendingRtdbActionDao()
        val id = dao.insert(
            PendingRtdbAction(
                path = path,
                type = RtdbActionType.SET,
                payloadJson = JsonUtil.mapToJson(value),
                priority = priority,
                dedupeKey = dedupeKey
            )
        )
        RtdbQueueWorkScheduler.enqueue(context)
        return id
    }

    suspend fun enqueueUpdateMap(
        context: Context,
        path: String,
        updates: Map<String, Any?>,
        priority: Int = 1,
        dedupeKey: String? = null
    ): Long {
        val dao = AppDatabase.get(context).pendingRtdbActionDao()
        val id = dao.insert(
            PendingRtdbAction(
                path = path,
                type = RtdbActionType.UPDATE,
                payloadJson = JsonUtil.mapToJson(updates),
                priority = priority,
                dedupeKey = dedupeKey
            )
        )
        RtdbQueueWorkScheduler.enqueue(context)
        return id
    }
}
