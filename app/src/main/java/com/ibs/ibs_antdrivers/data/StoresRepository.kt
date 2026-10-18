package com.ibs.ibs_antdrivers.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.StoreData
import com.ibs.ibs_antdrivers.cache.entities.StoreEntity
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.IOException

class StoresRepository(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference,
) {

    /** Pagination and search run entirely in Room, across the complete downloaded store directory. */
    fun observeStorePage(context: Context, search: StoreSearchQuery): Flow<StoreSearchPage> {
        val dao = AppDatabase.get(context).storeDao()
        return combine(
            dao.observePage(search.query, search.franchise, search.region, search.limit),
            dao.observeMatchCount(search.query, search.franchise, search.region),
        ) { stores, count -> StoreSearchPage(stores.map { it.toStoreData() }, count) }
            .distinctUntilChanged()
    }

    fun observeStoreFilters(context: Context): Flow<StoreFilterOptions> {
        val dao = AppDatabase.get(context).storeDao()
        return combine(dao.observeFranchises(), dao.observeRegions(), ::StoreFilterOptions)
            .distinctUntilChanged()
    }

    suspend fun refreshStoresIfNeeded(context: Context, force: Boolean = false): Boolean {
        val appContext = context.applicationContext
        return refreshStoreCache(
            AppDatabase.get(appContext).storeDao(),
            appContext.getSharedPreferences("stores_cache_sync", Context.MODE_PRIVATE),
            force,
        ) {
            val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) {
                throw IOException("Connect to the internet to refresh stores")
            }
            val snapshot = db.child("Stores").get().await()
            snapshot.children.map { it.toStoreData().toEntity() }
        }
    }

    fun observeAllStores(context: Context): Flow<List<StoreData>> {
        return AppDatabase.get(context).storeDao()
            .observeAll()
            .map { list ->
                list.map { it.toStoreData() }
            }
    }

    suspend fun getStoreById(storeId: String, context: Context): StoreData? {
        // All callers must check the persistent directory before requesting a store from Firebase.
        val cached = AppDatabase.get(context).storeDao().getById(storeId)
        if (cached != null) return cached.toStoreData()

        // 2) Fallback to RTDB (will also be cached by Firebase persistence)
        val snap = db.child("Stores").child(storeId).get().await()
        if (!snap.exists()) return null

        val store = snap.toStoreData().copy(StoreID = storeId)

        // 3) Save to Room for next time
        runCatching {
            AppDatabase.get(context).storeDao().upsertAll(listOf(store.toEntity()))
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

data class StoreSearchQuery(
    val query: String = "",
    val franchise: String? = null,
    val region: String? = null,
    val limit: Int = STORE_PAGE_SIZE,
) {
    companion object { const val STORE_PAGE_SIZE = 10 }
}

data class StoreSearchPage(val stores: List<StoreData>, val totalCount: Int)
data class StoreFilterOptions(val franchises: List<String>, val regions: List<String>)
