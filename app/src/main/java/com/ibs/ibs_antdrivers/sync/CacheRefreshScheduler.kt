package com.ibs.ibs_antdrivers.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object CacheRefreshScheduler {

    private const val STORES_WORK = "refresh_stores_cache"
    private const val PRICELISTS_WORK = "refresh_pricelists_cache"
    private const val ORDERS_WORK = "refresh_orders_cache"
    private const val TODAY_CC_WORK = "refresh_today_call_cycle"
    private const val PLANNED_CC_WORK = "refresh_planned_call_cycles"
    private const val CATALOGUE_CATS_WORK = "refresh_catalogue_categories_cache"

    fun refreshStores(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<RefreshStoresWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(STORES_WORK, ExistingWorkPolicy.KEEP, req)
    }

    fun refreshPriceLists(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<RefreshPriceListsWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(PRICELISTS_WORK, ExistingWorkPolicy.KEEP, req)
    }

    fun refreshOrders(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<RefreshOrdersWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(ORDERS_WORK, ExistingWorkPolicy.KEEP, req)
    }

    fun refreshTodayCallCycle(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<RefreshTodayCallCycleWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(TODAY_CC_WORK, ExistingWorkPolicy.REPLACE, req)
    }

    fun refreshPlannedCallCycles(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<RefreshPlannedCallCyclesWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(PLANNED_CC_WORK, ExistingWorkPolicy.REPLACE, req)
    }

    fun refreshCatalogueCategories(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<RefreshCatalogueCategoriesWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(CATALOGUE_CATS_WORK, ExistingWorkPolicy.REPLACE, req)
    }

    fun refreshAll(context: Context) {
        refreshStores(context)
        refreshPriceLists(context)
        refreshOrders(context)
        refreshTodayCallCycle(context)
        refreshPlannedCallCycles(context)
        refreshCatalogueCategories(context)
    }
}
