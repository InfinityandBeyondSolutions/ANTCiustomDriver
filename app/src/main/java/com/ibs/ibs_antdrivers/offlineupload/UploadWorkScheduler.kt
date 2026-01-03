package com.ibs.ibs_antdrivers.offlineupload

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object UploadWorkScheduler {
    private const val UNIQUE_WORK_NAME = "upload_pending_images"

    /**
     * Schedules a background upload pass.
     *
     * "Stable internet" here means unmetered (typically Wi‑Fi). If you want to allow mobile data,
     * change to NetworkType.CONNECTED.
     */
    fun enqueue(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadPendingImagesWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

