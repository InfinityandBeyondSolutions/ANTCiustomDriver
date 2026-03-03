package com.ibs.ibs_antdrivers

import android.app.Application
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

/**
 * App-wide init that must run before any FirebaseDatabase.getInstance() usage.
 *
 * Enables RTDB disk persistence so reads/writes can survive offline periods.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable RTDB persistence (must be called before the first database reference is created).
        try {
            val db = FirebaseDatabase.getInstance()
            db.setPersistenceEnabled(true)
            // Keep the cache size reasonable. Adjust if you want more offline history.
            db.setPersistenceCacheSizeBytes(50L * 1024L * 1024L) // 50MB
        } catch (t: Throwable) {
            // If this is called too late (after refs were created), Firebase throws.
            // Don't crash the app; we still want graceful offline behavior.
            Log.w("App", "RTDB persistence init skipped: ${t.message}")
        }

        // Best-effort: warm offline cache when connectivity is available.
        // This makes "first offline run" much more reliable.
        try {
            com.ibs.ibs_antdrivers.sync.OfflinePrefetchScheduler.enqueue(this)
        } catch (t: Throwable) {
            Log.w("App", "Offline prefetch schedule skipped: ${t.message}")
        }

        // Populate Room caches (offline-first datasets).
        try {
            com.ibs.ibs_antdrivers.sync.CacheRefreshScheduler.refreshAll(this)
        } catch (t: Throwable) {
            Log.w("App", "Room cache refresh schedule skipped: ${t.message}")
        }
    }
}
