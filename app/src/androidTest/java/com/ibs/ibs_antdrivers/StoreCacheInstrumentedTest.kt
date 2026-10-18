package com.ibs.ibs_antdrivers

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ibs.ibs_antdrivers.cache.entities.StoreEntity
import com.ibs.ibs_antdrivers.data.STORE_CACHE_MAX_AGE_MS
import com.ibs.ibs_antdrivers.data.refreshStoreCache
import com.ibs.ibs_antdrivers.offlineupload.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StoreCacheInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase
    private val preferences by lazy { context.getSharedPreferences("store_cache_test_${UUID.randomUUID()}", 0) }
    private val dao get() = database.storeDao()

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After fun cleanup() {
        database.close()
        preferences.edit().clear().commit()
    }

    @Test fun pagesLoadTenAtATimeAndSearchFindsStoresOutsideThosePages() = runBlocking {
        dao.replaceAll(stores().reversed())
        val first = dao.observePage("", null, null, 10).first()
        val second = dao.observePage("", null, null, 20).first()
        assertEquals((1..10).map(::id), first.map { it.storeId })
        assertEquals((1..20).map(::id), second.map { it.storeId })
        assertEquals(35, dao.observeMatchCount("", null, null).first())
        assertEquals(listOf(id(35)), dao.observePage("STORE 035", null, null, 10).first().map { it.storeId })
        assertEquals(listOf(id(35)), dao.observePage("id035", null, null, 10).first().map { it.storeId })
        assertTrue(dao.observePage("missing", null, null, 10).first().isEmpty())
        assertEquals(0, dao.observeMatchCount("missing", null, null).first())
    }

    @Test fun franchiseAndRegionFiltersApplyToTheWholeCache() = runBlocking {
        dao.replaceAll(stores())
        val matching = dao.observePage("", "Group B", "North", 10).first()
        assertEquals(listOf(6, 12, 18, 24, 30).map(::id), matching.map { it.storeId })
        assertEquals(5, dao.observeMatchCount("", "Group B", "North").first())
        assertEquals(listOf("Group A", "Group B"), dao.observeFranchises().first())
        assertEquals(listOf("North", "South"), dao.observeRegions().first())
    }

    @Test fun searchTreatsPercentAndUnderscoreAsLiteralText() = runBlocking {
        dao.replaceAll(listOf(
            store(1).copy(storeName = "100% Bulk"), store(2).copy(storeName = "1000 Bulk"),
            store(3).copy(storeName = "A_B Stores"), store(4).copy(storeName = "AXB Stores"),
        ))
        assertEquals(listOf(id(1)), dao.observePage("%", null, null, 10).first().map { it.storeId })
        assertEquals(listOf(id(3)), dao.observePage("_", null, null, 10).first().map { it.storeId })
    }

    @Test fun concurrentRequestsAndReopeningReuseOneDownloadUntilExpiry() = runBlocking {
        var calls = 0
        var time = 1000L
        suspend fun refresh(force: Boolean = false) = refreshStoreCache(dao, preferences, force, { time }) {
            calls++
            delay(30)
            stores()
        }
        val initial = (1..4).map { async { refresh() } }.awaitAll()
        assertEquals(1, initial.count { it })
        assertEquals(1, calls)
        repeat(3) { assertFalse(refresh()) }
        dao.observePage("store 035", null, null, 10).first()
        dao.observePage("", null, null, 20).first()
        assertEquals(1, calls)
        assertTrue(refresh(force = true))
        assertEquals(2, calls)
        time += STORE_CACHE_MAX_AGE_MS
        assertTrue(refresh())
        assertEquals(3, calls)
    }

    @Test fun failedOrCancelledRefreshKeepsSavedStoresAndAllowsRetry() = runBlocking {
        var time = 1000L
        refreshStoreCache(dao, preferences, now = { time }) { stores() }
        time += STORE_CACHE_MAX_AGE_MS
        try {
            refreshStoreCache(dao, preferences, now = { time }) { throw IOException("offline") }
            fail("Expected offline refresh to fail")
        } catch (_: IOException) { }
        assertEquals(35, dao.count())
        try {
            refreshStoreCache(dao, preferences, now = { time }) { throw CancellationException() }
            fail("Expected cancellation to propagate")
        } catch (_: CancellationException) { }
        assertEquals(35, dao.count())
        assertTrue(refreshStoreCache(dao, preferences, now = { time }) { listOf(store(35)) })
        assertEquals(listOf(id(35)), dao.observePage("", null, null, 10).first().map { it.storeId })
    }

    @Test fun emptySuccessfulSnapshotRemovesDeletedStoresAndRemainsCached() = runBlocking {
        refreshStoreCache(dao, preferences, now = { 1000 }) { stores() }
        assertTrue(refreshStoreCache(dao, preferences, force = true, now = { 2000 }) { emptyList() })
        assertEquals(0, dao.count())
        assertFalse(refreshStoreCache(dao, preferences, now = { 3000 }) { error("Unexpected Firebase read") })
    }

    private fun id(number: Int) = "ID%03d".format(number)
    private fun stores() = (1..35).map(::store)
    private fun store(number: Int) = StoreEntity(
        storeId = id(number), storeName = "Store %03d".format(number),
        storeRegion = if (number % 2 == 0) "North" else "South",
        storeFranchise = if (number % 3 == 0) "Group B" else "Group A",
        storeContactNum = "", repName = "", contactPerson = "", storeEmail = "", storeAddress = "",
    )
}
