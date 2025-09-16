package com.ibs.ibs_antdrivers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.service.LocationTrackingService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: SharedPreferences

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private fun todayKey(): String = dateFmt.format(Date())

    // Used to continue clock-in flow after permissions are granted
    private var pendingClockIn = false

    companion object {
        private const val PREFS = "app_prefs"
        private const val KEY_PERMS_REQUESTED = "permissions_requested"
        private const val KEY_TRACKING_ACTIVE = "tracking_active"
        private const val KEY_CLOCK_IN_AT = "clock_in_at"
        private const val KEY_ACTIVE_SHIFT_DATE = "active_shift_date"
        private const val KEY_ACTIVE_SHIFT_SESSION = "active_shift_session"
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(90) // 3 months
    }

    // --- Activity Result Launchers ---

    private val fineCoarseLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val fine = res[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = res[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            // Continue background permission if needed, otherwise continue clock-in if pending
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackground()) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else if (pendingClockIn) {
                pendingClockIn = false
                createShiftAndStartTracking()
            }
        } else {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
            openAppSettings()
            pendingClockIn = false
        }
        prefs.edit().putBoolean(KEY_PERMS_REQUESTED, true).apply()
    }

    private val backgroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Regardless of granted/denied, continue the clock-in flow;
        // startTrackingIfEnabledAndAllowed() will enforce actual checks.
        if (pendingClockIn) {
            pendingClockIn = false
            createShiftAndStartTracking()
        }
    }

    private val notificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional: show a toast */ }

    // --- Lifecycle ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        if (auth.currentUser == null) {
            startActivity(Intent(this, Login::class.java))
            finish()
            return
        }

        // Setup nav
        val navView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navView.setupWithNavController(navHostFragment.navController)

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Stop any alarm sound just in case
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(this, uri)
            if (ringtone?.isPlaying == true) ringtone.stop()
        } catch (_: Exception) {}

        // Ensure DB online at app start (safe-guard if previous run went offline)
        FirebaseDatabase.getInstance().goOnline()

        cleanupOldShifts()

        // If tracking was active before process death, we can resume the service
        if (prefs.getBoolean(KEY_TRACKING_ACTIVE, false)) {
            startTrackingIfEnabledAndAllowed()
        }

        Toast.makeText(this, "Meerkat Tracking Service Ready", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }

    // --- Public API for DriverHomeFragment ---

    fun isTrackingActive(): Boolean = prefs.getBoolean(KEY_TRACKING_ACTIVE, false)
    fun getClockInAt(): Long = prefs.getLong(KEY_CLOCK_IN_AT, 0L)

    fun clockIn() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }
        // Request permissions if needed, then proceed
        if (!hasFineOrCoarse()) {
            pendingClockIn = true
            fineCoarseLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackground()) {
            pendingClockIn = true
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }
        createShiftAndStartTracking()
    }

    fun clockOut() {
        if (auth.currentUser == null) return
        stopTrackingAndCloseShift()
    }

    // --- Permissions & Settings helpers ---

    private fun hasFineOrCoarse(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasBackground(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    // --- Tracking lifecycle ---

    private fun startTrackingIfEnabledAndAllowed() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bgOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        else true

        if (!(fine || coarse) || !bgOk) return
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Enable location services", Toast.LENGTH_LONG).show()
            return
        }
        startLocationService()
        // Do NOT toggle online status here; that’s tied to clock-in/out only.
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = "ACTION_START"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Location tracking active", Toast.LENGTH_SHORT).show()
    }

    private fun stopLocationService() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = "ACTION_STOP"
        }
        startService(intent)
        Toast.makeText(this, "Location tracking stopped", Toast.LENGTH_SHORT).show()
    }

    // --- Shift management (users/{uid}/shifts/{yyyy-MM-dd}/{sessionId}) ---

    private fun createShiftAndStartTracking() {
        val uid = auth.currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance()
        db.goOnline() // ensure online for the write

        val dateKey = todayKey()
        val now = System.currentTimeMillis()

        val sessionRef = db.reference
            .child("users").child(uid)
            .child("shifts").child(dateKey)
            .push() // unique session under the day

        val record = mapOf(
            "clockIn" to now,
            "active" to true
        )

        sessionRef.setValue(record)
            .addOnSuccessListener {
                // Save active session identifiers
                prefs.edit()
                    .putBoolean(KEY_TRACKING_ACTIVE, true)
                    .putString(KEY_ACTIVE_SHIFT_DATE, dateKey)
                    .putString(KEY_ACTIVE_SHIFT_SESSION, sessionRef.key)
                    .putLong(KEY_CLOCK_IN_AT, now)
                    .apply()

                // Start tracking (keep app open)
                startTrackingIfEnabledAndAllowed()

                // Flip device online flag
                setDriverOnlineFlag(true)

                Toast.makeText(this, "Clocked in", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to clock in: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun stopTrackingAndCloseShift() {
        val uid = auth.currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance()
        val dateKey = prefs.getString(KEY_ACTIVE_SHIFT_DATE, todayKey()) ?: todayKey()
        val sessionId = prefs.getString(KEY_ACTIVE_SHIFT_SESSION, null)
        val now = System.currentTimeMillis()

        // Stop tracking first; keep DB online until write completes
        stopLocationService()

        fun finalizeAndGoOffline() {
            // Clear local state
            prefs.edit()
                .putBoolean(KEY_TRACKING_ACTIVE, false)
                .remove(KEY_ACTIVE_SHIFT_DATE)
                .remove(KEY_ACTIVE_SHIFT_SESSION)
                .remove(KEY_CLOCK_IN_AT)
                .apply()
            // Now it’s safe to go offline
            db.goOffline()
        }

        fun closeByRef(ref: com.google.firebase.database.DatabaseReference) {
            val updates = mapOf(
                "clockOut" to now,
                "active" to false
            )
            ref.updateChildren(updates)
                .addOnSuccessListener {
                    // Flip device offline
                    setDriverOnlineFlag(false)
                    finalizeAndGoOffline()
                    Toast.makeText(this, "Clocked out", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to clock out: ${e.message}", Toast.LENGTH_LONG).show()
                    // Stay online so it can retry later
                }
        }

        if (sessionId != null) {
            val ref = db.reference.child("users").child(uid)
                .child("shifts").child(dateKey).child(sessionId)
            closeByRef(ref)
        } else {
            // Fallback: find latest active session today and close it
            val dayRef = db.reference.child("users").child(uid).child("shifts").child(dateKey)
            dayRef.get().addOnSuccessListener { snap ->
                var latestKey: String? = null
                var latestClockIn = 0L
                for (child in snap.children) {
                    val active = child.child("active").getValue(Boolean::class.java) ?: false
                    val cin = child.child("clockIn").getValue(Long::class.java) ?: 0L
                    if (active && cin >= latestClockIn) {
                        latestClockIn = cin
                        latestKey = child.key
                    }
                }
                if (latestKey != null) {
                    closeByRef(dayRef.child(latestKey!!))
                } else {
                    // Nothing to close; still flip device offline and clear state
                    setDriverOnlineFlag(false)
                    finalizeAndGoOffline()
                    Toast.makeText(this, "No active session found; set offline", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to read sessions: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cleanupOldShifts() {
        val uid = auth.currentUser?.uid ?: return
        val cutoffCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -90) }
        val cutoffKey = dateFmt.format(cutoffCal.time) // yyyy-MM-dd

        val ref = FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("shifts")

        // Keys are yyyy-MM-dd, so lexicographic order matches chronological
        ref.get().addOnSuccessListener { snap ->
            val deletes = hashMapOf<String, Any?>()
            for (child in snap.children) {
                val dateKey = child.key ?: continue
                if (dateKey < cutoffKey) {
                    deletes[dateKey] = null // delete entire day node (and all its sessions)
                }
            }
            if (deletes.isNotEmpty()) ref.updateChildren(deletes)
        }
    }

    // --- Driver device status toggles (drivers/{uid}/deviceStatus) ---

    private fun setDriverOnlineFlag(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        val ref = FirebaseDatabase.getInstance().reference
            .child("drivers").child(uid).child("deviceStatus")

        val updates = mapOf(
            "online" to online,
            "timestamp" to now
        )
        ref.updateChildren(updates)

        // Optional: keep info.lastSeen fresh
        FirebaseDatabase.getInstance().reference
            .child("drivers").child(uid).child("info").child("lastSeen").setValue(now)
    }

    // --- Utilities ---

    private fun getBatteryPct(context: Context): Int {
        return try {
            val bm = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = bm?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bm?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else -1
        } catch (_: Exception) {
            -1
        }
    }

    // --- Optional sign out ---

    private fun signOut() {
        stopLocationService()
        setDriverOnlineFlag(false)
        auth.signOut()
        startActivity(Intent(this, Login::class.java))
        finish()
    }
}