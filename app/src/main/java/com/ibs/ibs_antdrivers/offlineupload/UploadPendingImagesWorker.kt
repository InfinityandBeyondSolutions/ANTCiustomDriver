package com.ibs.ibs_antdrivers.offlineupload

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class UploadPendingImagesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val dao = db.uploadImageDao()

        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference

        // Process in small batches to avoid long single runs.
        val batch = dao.getNextPending(limit = 15)
        if (batch.isEmpty()) return Result.success()

        var anyTransientFailure = false

        for (item in batch) {
            val file = File(item.localFilePath)
            if (!file.exists()) {
                dao.updateStatus(
                    id = item.id,
                    status = UploadStatus.FAILED,
                    retryCount = item.retryCount + 1,
                    lastError = "Local file missing"
                )
                continue
            }

            try {
                dao.updateStatus(item.id, UploadStatus.UPLOADING, item.retryCount, null)

                val remoteRef = storageRef.child(item.remotePath)
                val uri = Uri.fromFile(file)

                // Upload. If it fails due to network, we'll retry.
                remoteRef.putFile(uri).await()

                dao.updateStatus(item.id, UploadStatus.UPLOADED, item.retryCount, null)

                // Best-effort cleanup: remove local file after successful upload.
                runCatching { file.delete() }
            } catch (t: Throwable) {
                Log.e("UploadWorker", "Upload failed id=${item.id} path=${item.remotePath}: ${t.message}")

                val retryCount = item.retryCount + 1
                dao.updateStatus(item.id, UploadStatus.FAILED, retryCount, t.message)

                // Treat most failures as transient: allow WorkManager backoff/retry.
                anyTransientFailure = true
            }
        }

        // If there are still pending items, schedule another pass.
        // Returning retry will cause WorkManager to run again with backoff.
        return if (anyTransientFailure) Result.retry() else Result.success()
    }
}

