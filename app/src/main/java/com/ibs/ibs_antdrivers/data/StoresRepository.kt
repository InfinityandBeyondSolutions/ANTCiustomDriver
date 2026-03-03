package com.ibs.ibs_antdrivers.data

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.StoreData
import com.ibs.ibs_antdrivers.cache.entities.StoreEntity
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StoresRepository(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {

    fun observeAllStores(context: Context): Flow<List<StoreData>> {
        return AppDatabase.get(context).storeDao()
            .observeAll()
            .map { list ->
                list.map { it.toStoreData() }
            }
    }

    suspend fun getStoreById(storeId: String, context: Context? = null): StoreData? {
        // 1) Room first (if available)
        if (context != null) {
            val cached = AppDatabase.get(context).storeDao().getById(storeId)
            if (cached != null) return cached.toStoreData()
        }

        // 2) Fallback to RTDB (will also be cached by Firebase persistence)
        val snap = db.child("Stores").child(storeId).get().await()
        if (!snap.exists()) return null

        val store = snap.toStoreData().copy(StoreID = storeId)

        // 3) Save to Room for next time
        if (context != null) {
            runCatching {
                AppDatabase.get(context).storeDao().upsertAll(listOf(store.toEntity()))
            }
        }

        return store
    }

    private fun StoreEntity.toStoreData(): StoreData {
        return StoreData(
            StoreID = storeId,
            StoreName = storeName,
            StoreRegion = storeRegion,
            StoreFranchise = storeFranchise,
            StoreContactNum = storeContactNum,
            RepName = repName,
            ContactPerson = contactPerson,
            StoreEmail = storeEmail,
            StoreAddress = storeAddress,
        )
    }

    private fun StoreData.toEntity(): StoreEntity {
        return StoreEntity(
            storeId = StoreID,
            storeName = StoreName,
            storeRegion = StoreRegion,
            storeFranchise = StoreFranchise,
            storeContactNum = StoreContactNum,
            repName = RepName,
            contactPerson = ContactPerson,
            storeEmail = StoreEmail,
            storeAddress = StoreAddress,
        )
    }

    private fun DataSnapshot.toStoreData(): StoreData {
        return StoreData(
            StoreID = key ?: "",
            StoreName = child("StoreName").getValue(String::class.java) ?: "",
            StoreRegion = child("StoreRegion").getValue(String::class.java) ?: "",
            StoreFranchise = child("StoreFranchise").getValue(String::class.java) ?: "",
            StoreContactNum = child("StoreContactNum").getValue(String::class.java) ?: "",
            RepName = child("RepName").getValue(String::class.java) ?: "",
            ContactPerson = child("ContactPerson").getValue(String::class.java) ?: "",
            StoreEmail = child("StoreEmail").getValue(String::class.java) ?: "",
            StoreAddress = child("StoreAddress").getValue(String::class.java) ?: "",
        )
    }
}
