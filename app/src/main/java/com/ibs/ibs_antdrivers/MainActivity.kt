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
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

    private var pendingClockIn = false

    companion object {
        private const val PREFS = "app_prefs"
        private const val KEY_PERMS_REQUESTED = "permissions_requested"
        private const val KEY_TRACKING_ACTIVE = "tracking_active"
        private const val KEY_CLOCK_IN_AT = "clock_in_at"
        private const val KEY_ACTIVE_SHIFT_DATE = "active_shift_date"
        private const val KEY_ACTIVE_SHIFT_SESSION = "active_shift_session"
        private const val KEY_BATTERY_DIALOG_SHOWN = "battery_dialog_shown"
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(90) // 3 months
    }

    // --- Activity Result Launchers ---

    private val fineCoarseLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val fine = res[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = res[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
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
        if (pendingClockIn) {
            pendingClockIn = false
            createShiftAndStartTracking()
        }
    }

    private val notificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    // --- Lifecycle ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideStatusBar()

        auth = FirebaseAuth.getInstance()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        if (auth.currentUser == null) {
            startActivity(Intent(this, Login::class.java))
            finish()
            return
        }

        val navView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navView.setupWithNavController(navHostFragment.navController)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(this, uri)
            if (ringtone?.isPlaying == true) ringtone.stop()
        } catch (_: Exception) {}

        FirebaseDatabase.getInstance().goOnline()

        cleanupOldShifts()
        ensureDriverInfoBase()

        // ✅ Resume tracking if user was already clocked in
        if (prefs.getBoolean(KEY_TRACKING_ACTIVE, false)) {
            startTrackingIfEnabledAndAllowed()
            updateDriverSessionInfo(true)
        }

        Toast.makeText(this, "Ace Nut Tracking Service Ready", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            startActivity(Intent(this, Login::class.java))
            finish()
        } else {
            updateDriverSessionInfo(isActive = prefs.getBoolean(KEY_TRACKING_ACTIVE, false))
        }
    }

    // --- Public API ---

    fun isTrackingActive(): Boolean = prefs.getBoolean(KEY_TRACKING_ACTIVE, false)
    fun getClockInAt(): Long = prefs.getLong(KEY_CLOCK_IN_AT, 0L)

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        supportActionBar?.hide()
    }

    fun clockIn() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }
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

    // --- Permissions helpers ---

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

    // --- Shift management ---

    private fun createShiftAndStartTracking() {
        val uid = auth.currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance()
        db.goOnline()

        val dateKey = todayKey()
        val now = System.currentTimeMillis()

        val sessionRef = db.reference
            .child("users").child(uid)
            .child("shifts").child(dateKey)
            .push()

        val record = mapOf(
            "clockIn" to now,
            "active" to true
        )

        sessionRef.setValue(record)
            .addOnSuccessListener {
                prefs.edit()
                    .putBoolean(KEY_TRACKING_ACTIVE, true)
                    .putString(KEY_ACTIVE_SHIFT_DATE, dateKey)
                    .putString(KEY_ACTIVE_SHIFT_SESSION, sessionRef.key)
                    .putLong(KEY_CLOCK_IN_AT, now)
                    .apply()

                startTrackingIfEnabledAndAllowed()
                updateDriverSessionInfo(true)

                if (!prefs.getBoolean(KEY_BATTERY_DIALOG_SHOWN, false)) {
                    checkAndRequestBatteryOptimization()
                    prefs.edit().putBoolean(KEY_BATTERY_DIALOG_SHOWN, true).apply()
                }

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

        stopLocationService()

        if (sessionId != null) {
            val ref = db.reference.child("users").child(uid)
                .child("shifts").child(dateKey).child(sessionId)

            ref.updateChildren(mapOf("clockOut" to now, "active" to false))
                .addOnSuccessListener {
                    prefs.edit()
                        .putBoolean(KEY_TRACKING_ACTIVE, false)
                        .remove(KEY_ACTIVE_SHIFT_DATE)
                        .remove(KEY_ACTIVE_SHIFT_SESSION)
                        .remove(KEY_CLOCK_IN_AT)
                        .apply()

                    updateDriverSessionInfo(false)
                    db.goOffline()
                    Toast.makeText(this, "Clocked out", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to clock out: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            updateDriverSessionInfo(false)
            prefs.edit()
                .putBoolean(KEY_TRACKING_ACTIVE, false)
                .remove(KEY_ACTIVE_SHIFT_DATE)
                .remove(KEY_ACTIVE_SHIFT_SESSION)
                .remove(KEY_CLOCK_IN_AT)
                .apply()
            db.goOffline()
            Toast.makeText(this, "No active session found; set offline", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanupOldShifts() {
        val uid = auth.currentUser?.uid ?: return
        val cutoffCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -90) }
        val cutoffKey = dateFmt.format(cutoffCal.time)

        val ref = FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("shifts")

        ref.get().addOnSuccessListener { snap ->
            val deletes = hashMapOf<String, Any?>()
            for (child in snap.children) {
                val dateKey = child.key ?: continue
                if (dateKey < cutoffKey) {
                    deletes[dateKey] = null
                }
            }
            if (deletes.isNotEmpty()) ref.updateChildren(deletes)
        }
    }

    // --- Driver Info Management ---

    private fun ensureDriverInfoBase() {
        val uid = auth.currentUser?.uid ?: return
        val email = auth.currentUser?.email ?: ""
        val db = FirebaseDatabase.getInstance().reference

        db.child("users").child(uid).get().addOnSuccessListener { snap ->
            val firstName = snap.child("firstName").getValue(String::class.java) ?: ""
            val lastName = snap.child("lastName").getValue(String::class.java) ?: ""
            val name = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

            val infoBase = mapOf(
                "email" to email,
                "userId" to uid,
                "name" to name
            )

            db.child("drivers").child(uid).child("info").updateChildren(infoBase)
        }
    }


    private fun updateDriverSessionInfo(isActive: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()

        val db = FirebaseDatabase.getInstance().reference

        // Update driver info
        val sessionInfo = mapOf(
            "isActive" to isActive,
            "lastSeen" to now
        )
        db.child("drivers").child(uid).child("info").updateChildren(sessionInfo)


        val deviceStatus = mapOf(
            "online" to isActive
        )
        db.child("drivers").child(uid).child("deviceStatus").updateChildren(deviceStatus)
    }


//    private fun updateDriverSessionInfo(isActive: Boolean) {
//        val uid = auth.currentUser?.uid ?: return
//        val now = System.currentTimeMillis()
//
//        val sessionInfo = mapOf(
//            "isActive" to isActive,
//            "lastSeen" to now
//        )
//
//        FirebaseDatabase.getInstance().reference
//            .child("drivers").child(uid).child("info")
//            .updateChildren(sessionInfo)
//    }

    // --- Battery Optimization Handling ---

    private fun checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog()
            }
        }
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Allow Background Tracking")
            .setMessage("To keep your trips tracked reliably, please allow this app to run without battery optimization. Otherwise, tracking may stop when your phone is idle.")
            .setPositiveButton("Allow") { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Utilities ---

    private fun signOut() {
        stopLocationService()
        updateDriverSessionInfo(false)
        auth.signOut()
        startActivity(Intent(this, Login::class.java))
        finish()
    }
}
