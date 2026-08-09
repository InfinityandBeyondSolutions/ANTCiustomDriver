package com.ibs.ibs_antdrivers.rtdbqueue

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.tasks.await

class FlushPendingRtdbActionsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) return Result.success() // nothing to do if signed out

        val db = AppDatabase.get(applicationContext)
        val dao = db.pendingRtdbActionDao()

        val batch = dao.nextBatch(limit = 25)
        if (batch.isEmpty()) return Result.success()

        var anyTransientFailure = false
        val firebaseRoot = FirebaseDatabase.getInstance().reference

        for (item in batch) {
            try {
                dao.updateStatus(item.id, PendingStatus.RUNNING)

                val ref = firebaseRoot.child(item.path)

                val queuedOrderId = queuedOrderIdOrNull(item.path, item.type)
                if (queuedOrderId != null && orderAlreadyExistsOrIsArchived(firebaseRoot, queuedOrderId)) {
                    // SET is a creation retry, not an update. Replaying it over an existing order
                    // could reset Processing/Completed back to New; replaying it after archival
                    // would recreate a completed order in the active Kanban table.
                    Log.i("RtdbQueueWorker", "Discarded stale order create path=${item.path}")
                    dao.updateResult(item.id, PendingStatus.DONE, item.retryCount, null)
                    continue
                }

                when (item.type) {
                    RtdbActionType.SET -> {
                        val raw = item.payloadJson
                        val value: Any? = if (raw != null && raw.trim().startsWith("{")) {
                            // Structured payload
                            JsonUtil.jsonToMap(raw)
                        } else {
                            // Simple string payload
                            raw
                        }
                        ref.setValue(value).await()
                    }

                    RtdbActionType.UPDATE -> {
                        val json = item.payloadJson ?: "{}"
                        val map = if (json.trim().startsWith("{")) {
                            JsonUtil.jsonToMap(json)
                        } else {
                            emptyMap()
                        }
                        ref.updateChildren(map).await()
                    }

                    RtdbActionType.DELETE -> {
                        ref.removeValue().await()
                    }
                }

                dao.updateResult(item.id, PendingStatus.DONE, item.retryCount, null)
            } catch (t: Throwable) {
                Log.w("RtdbQueueWorker", "Failed id=${item.id} path=${item.path}: ${t.message}")

                val retryCount = item.retryCount + 1
                dao.updateResult(item.id, PendingStatus.FAILED, retryCount, t.message)

                anyTransientFailure = true
            }
        }

        // Cleanup succeeded items.
        runCatching { dao.deleteDone() }

        // If there are still items, schedule another pass.
        val more = dao.countPending() > 0
        if (more) RtdbQueueWorkScheduler.enqueue(applicationContext)

        return if (anyTransientFailure) Result.retry() else Result.success()
    }

    private suspend fun orderAlreadyExistsOrIsArchived(
        root: com.google.firebase.database.DatabaseReference,
        orderId: String
    ): Boolean {
        if (root.child("orders").child(orderId).get().await().exists()) return true

        // Current web versions archive under completedOrders/{originalOrderId}.
        if (root.child("completedOrders").child(orderId).get().await().exists()) return true

        // Legacy web versions used POST and generated a different archive child key, while
        // retaining the source key in the payload's id field.
        return root.child("completedOrders")
            .orderByChild("id")
            .equalTo(orderId)
            .limitToFirst(1)
            .get()
            .await()
            .exists()
    }
}

internal fun queuedOrderIdOrNull(path: String, type: RtdbActionType): String? {
    if (type != RtdbActionType.SET) return null

    val segments = path.trim('/').split('/')
    if (segments.size != 2 || segments[0] != "orders") return null

    return segments[1].takeIf { it.isNotBlank() }
}
