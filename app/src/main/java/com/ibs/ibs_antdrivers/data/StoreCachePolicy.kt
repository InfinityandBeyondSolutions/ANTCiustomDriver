package com.ibs.ibs_antdrivers.data

import android.content.SharedPreferences
import com.ibs.ibs_antdrivers.cache.dao.StoreDao
import com.ibs.ibs_antdrivers.cache.entities.StoreEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val STORE_CACHE_MAX_AGE_MS = 6L * 60 * 60 * 1000
private const val LAST_FULL_SYNC = "last_full_sync"
private const val FULL_SYNC_COUNT = "full_sync_count"
private val storeSyncMutex = Mutex()

internal fun storeCacheNeedsRefresh(lastSync: Long, now: Long, count: Int, expectedCount: Int): Boolean =
    lastSync <= 0 || now < lastSync || now - lastSync >= STORE_CACHE_MAX_AGE_MS ||
        expectedCount < 0 || count < expectedCount

/** All entry points share this lock and freshness marker, including workers and manual refresh. */
internal suspend fun refreshStoreCache(
    dao: StoreDao,
    preferences: SharedPreferences,
    force: Boolean = false,
    now: () -> Long = System::currentTimeMillis,
    download: suspend () -> List<StoreEntity>,
): Boolean = storeSyncMutex.withLock {
    val stale = storeCacheNeedsRefresh(
        preferences.getLong(LAST_FULL_SYNC, 0), now(), dao.count(),
        preferences.getInt(FULL_SYNC_COUNT, -1),
    )
    if (!force && !stale) return@withLock false

    val stores = download()
    // A failed download or transaction leaves the previous searchable cache intact.
    dao.replaceAll(stores)
    preferences.edit().putLong(LAST_FULL_SYNC, now()).putInt(FULL_SYNC_COUNT, stores.size).apply()
    true
}
