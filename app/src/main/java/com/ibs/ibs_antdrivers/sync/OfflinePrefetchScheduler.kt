package com.ibs.ibs_antdrivers.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Schedules a best-effort offline prefetch pass.
 *
 * Downloads legacy calling-cycle paths while online. Dedicated cache refresh workers handle
 * stores, price lists, orders and catalogue metadata without a duplicate download here.
 */
object OfflinePrefetchScheduler {

    private const val UNIQUE_WORK_NAME = "offline_prefetch"

    fun enqueue(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<OfflinePrefetchWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
