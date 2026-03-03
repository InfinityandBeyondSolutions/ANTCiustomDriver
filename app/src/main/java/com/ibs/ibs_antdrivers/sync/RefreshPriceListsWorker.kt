package com.ibs.ibs_antdrivers.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.cache.entities.PriceListEntity
import com.ibs.ibs_antdrivers.cache.entities.PriceListItemEntity
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.tasks.await

/**
 * Downloads all price lists (and items) and stores them in Room.
 */
class RefreshPriceListsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val snap = FirebaseDatabase.getInstance().reference.child("priceLists").get().await()
            if (!snap.exists()) return Result.success()

            val lists = mutableListOf<PriceListEntity>()
            val items = mutableListOf<PriceListItemEntity>()

            for (plNode in snap.children) {
                val keyId = plNode.key ?: continue
                val id = plNode.child("id").getValue(String::class.java) ?: keyId

                lists += PriceListEntity(
                    priceListId = id,
                    title = plNode.child("title").getValue(String::class.java) ?: "",
                    name = plNode.child("name").getValue(String::class.java) ?: "",
                    companyName = plNode.child("companyName").getValue(String::class.java) ?: "",
                    effectiveDate = plNode.child("effectiveDate").getValue(String::class.java) ?: "",
                    status = plNode.child("status").getValue(String::class.java) ?: "",
                    includeVat = plNode.child("includeVat").getValue(Boolean::class.java) ?: false,
                    updatedAt = plNode.child("updatedAt").getValue(String::class.java) ?: "",
                    createdAt = plNode.child("createdAt").getValue(String::class.java) ?: "",
                )

                val itemsNode = plNode.child("items")
                if (itemsNode.exists()) {
                    for (itemNode in itemsNode.children) {
                        val itemKey = itemNode.key ?: ""
                        val itemId = itemNode.child("id").getValue(String::class.java) ?: itemKey
                        items += PriceListItemEntity(
                            priceListId = id,
                            itemId = itemId,
                            itemNo = itemNode.child("itemNo").getValue(String::class.java) ?: "",
                            description = itemNode.child("description").getValue(String::class.java) ?: "",
                            brand = itemNode.child("brand").getValue(String::class.java) ?: "",
                            size = itemNode.child("size").getValue(String::class.java) ?: "",
                            unitBarcode = itemNode.child("unitBarcode").getValue(String::class.java) ?: "",
                            outerBarcode = itemNode.child("outerBarcode").getValue(String::class.java) ?: "",
                            unitPrice = itemNode.child("unitPrice").getValue(String::class.java) ?: "",
                            casePrice = itemNode.child("casePrice").getValue(String::class.java) ?: "",
                        )
                    }
                }
            }

            val db = AppDatabase.get(applicationContext)
            db.priceListDao().deleteAllItems()
            db.priceListDao().deleteAllLists()
            db.priceListDao().upsertLists(lists)
            db.priceListDao().upsertItems(items)

            Result.success()
        } catch (t: Throwable) {
            Log.w("RefreshPriceListsWorker", "Failed: ${t.message}")
            Result.retry()
        }
    }
}

