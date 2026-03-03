package com.ibs.ibs_antdrivers.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.cache.entities.StoreEntity
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.tasks.await

/**
 * Downloads ALL stores from RTDB and stores them in Room for offline-first access.
 */
class RefreshStoresWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val snap = FirebaseDatabase.getInstance().reference.child("Stores").get().await()
            if (!snap.exists()) return Result.success()

            val entities = snap.children.mapNotNull { node ->
                val storeId = node.key ?: return@mapNotNull null
                StoreEntity(
                    storeId = storeId,
                    storeName = node.child("StoreName").getValue(String::class.java) ?: "",
                    storeRegion = node.child("StoreRegion").getValue(String::class.java) ?: "",
                    storeFranchise = node.child("StoreFranchise").getValue(String::class.java) ?: "",
                    storeContactNum = node.child("StoreContactNum").getValue(String::class.java) ?: "",
                    repName = node.child("RepName").getValue(String::class.java) ?: "",
                    contactPerson = node.child("ContactPerson").getValue(String::class.java) ?: "",
                    storeEmail = node.child("StoreEmail").getValue(String::class.java) ?: "",
                    storeAddress = node.child("StoreAddress").getValue(String::class.java) ?: "",
                )
            }

            val db = AppDatabase.get(applicationContext)
            db.storeDao().deleteAll()
            db.storeDao().upsertAll(entities)

            Result.success()
        } catch (t: Throwable) {
            Log.w("RefreshStoresWorker", "Failed: ${t.message}")
            Result.retry()
        }
    }
}
