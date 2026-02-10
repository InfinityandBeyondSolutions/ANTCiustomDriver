package com.ibs.ibs_antdrivers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ibs.ibs_antdrivers.data.TopicSubscriber
import com.ibs.ibs_antdrivers.service.LocationTrackingService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: SharedPreferences

    private lateinit var bottomNavView: BottomNavigationView
    private lateinit var navController: NavController

    private var authStateListener: FirebaseAuth.AuthStateListener? = null
   // private var tokenCheckRunnable: Runnable? = null
   // private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val askPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private fun todayKey(): String = dateFmt.format(Date())

    private var pendingClockIn = false

    companion object {
        private const val TOKEN_CHECK_INTERVAL_MS = 30_000L // Check every 30 seconds
        private const val PREFS = "app_prefs"
        private const val KEY_PERMS_REQUESTED = "permissions_requested"
        private const val KEY_TRACKING_ACTIVE = "tracking_active"
        private const val KEY_CLOCK_IN_AT = "clock_in_at"
        private const val KEY_ACTIVE_SHIFT_DATE = "active_shift_date"
        private const val KEY_ACTIVE_SHIFT_SESSION = "active_shift_session"
        private const val KEY_BATTERY_DIALOG_SHOWN = "battery_dialog_shown"
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(90)
    }

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
    ) { /* nop */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applySavedMode(this)
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        // Enforce sign-in + 7-day cookie.
        if (!isLoggedInWithValidCookie()) {
            goToLoginAndFinish()
            return
        }

        // Initialize app normally - Firebase Auth prevents disabled accounts from authenticating
        initializeApp()
    }

    private fun initializeApp() {
        setContentView(R.layout.activity_main)
        hideStatusBar()

        ensureNotificationChannel()
        requestPostNotificationIfNeeded()

        // ---- Navigation Component wiring (nav_graph.xml only) ----
        bottomNavView = findViewById(R.id.bottom_navigation)
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        bottomNavView.setupWithNavController(navController)

        // Setup bottom navigation animations
        setupBottomNavAnimations()

        // Deep link from FCM
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        FirebaseDatabase.getInstance().goOnline()

        cleanupOldShifts()
        ensureDriverInfoBase()

        if (prefs.getBoolean(KEY_TRACKING_ACTIVE, false)) {
            startTrackingIfEnabledAndAllowed()
            updateDriverSessionInfo(true)
        }

        // Monitor Firebase Auth state for session invalidation
        startAuthStateMonitoring()

        // Start periodic token validation check
       // startPeriodicTokenCheck()

        Toast.makeText(this, "Ace Nut Tracking Service Ready", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (!isLoggedInWithValidCookie()) {
            goToLoginAndFinish()
            return
        }

        // Immediately check token validity when app resumes
        //checkTokenValidity()

        // Resume periodic checks
       // startPeriodicTokenCheck()

        updateDriverSessionInfo(isActive = prefs.getBoolean(KEY_TRACKING_ACTIVE, false))
    }

    override fun onPause() {
        super.onPause()
        // Pause periodic checks to save battery when app is backgrounded
        //stopPeriodicTokenCheck()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

//    override fun onStart() {
//        super.onStart()
//        auth.currentUser?.uid?.let { uid ->
//            TopicSubscriber.subscribeToMyGroups(uid)
//        }
//    }

    override fun onStart() {
        super.onStart()

        val user = auth.currentUser ?: return

        user.reload()
            .addOnFailureListener { e ->
                if (e is com.google.firebase.auth.FirebaseAuthException &&
                    e.errorCode == "ERROR_USER_DISABLED"
                ) {
                    forceLogoutDisabled()
                }
                // ⚠️ Ignore all other errors (network, timeout, etc.)
            }
    }


    private fun isLoggedInWithValidCookie(): Boolean {
        // Must have Firebase user AND an unexpired 7-day cookie.
        if (auth.currentUser == null) return false
        return SessionPrefs.validateOrClear(this)
    }

    private fun goToLoginAndFinish() {
        startActivity(Intent(this, Login::class.java))
        finish()
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        supportActionBar?.hide()
    }

    /**
     * Sets up smooth scale animations for bottom navigation items.
     * Selected items scale up slightly for emphasis, while deselected items scale back to normal.
     */
    private fun setupBottomNavAnimations() {
        // Scale values for animation
        val scaleSelected = 1.15f
        val scaleDefault = 1.0f
        val animDuration = 200L

        // Apply initial state based on currently selected item
        bottomNavView.post {
            animateNavItemSelection(bottomNavView.selectedItemId, scaleSelected, scaleDefault, 0L)
        }

        // Listen for selection changes
        bottomNavView.setOnItemSelectedListener { item ->
            // Animate the selection change
            animateNavItemSelection(item.itemId, scaleSelected, scaleDefault, animDuration)

            // Let the NavController handle the navigation
            navController.navigate(item.itemId)
            true
        }
    }

    /**
     * Animates the scale of nav item icons based on selection state.
     */
    private fun animateNavItemSelection(
        selectedItemId: Int,
        scaleSelected: Float,
        scaleDefault: Float,
        duration: Long
    ) {
        val menu = bottomNavView.menu
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            val itemView = bottomNavView.findViewById<View>(menuItem.itemId) ?: continue

            val targetScale = if (menuItem.itemId == selectedItemId) scaleSelected else scaleDefault

            if (duration > 0) {
                itemView.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(duration)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                    .start()
            } else {
                itemView.scaleX = targetScale
                itemView.scaleY = targetScale
            }
        }
    }

    // --- Public API ---

    fun isTrackingActive(): Boolean = prefs.getBoolean(KEY_TRACKING_ACTIVE, false)
    fun getClockInAt(): Long = prefs.getLong(KEY_CLOCK_IN_AT, 0L)

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

    private fun handleIntent(intent: Intent?) {
        val deeplink = intent?.getStringExtra("deeplink")
        val groupIdExtra = intent?.getStringExtra("groupId")
        val groupId = groupIdExtra ?: extractGroupIdFromUrl(deeplink)
        if (!groupId.isNullOrBlank()) {
            // NOTE: chat screens are currently implemented via manual fragment transactions.
            // To keep navigation graph-only, those screens should be added as destinations.
            // For now we route the user back to the Home destination.
            navController.navigate(R.id.navHomeDriver)
        }
    }

    private fun extractGroupIdFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.substringAfter("/chat/", missingDelimiterValue = "").takeIf { it.isNotBlank() }
    }

    /* ---------- Notification Channel ---------- */

    private fun ensureNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            "chat_messages",
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName("Chat Messages")
            .setDescription("Group chat notifications")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun requestPostNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val ok = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) askPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

    override fun onDestroy() {
        super.onDestroy()
        stopAuthStateMonitoring()
       // stopPeriodicTokenCheck()
    }

    // --- Auth State Monitoring ---

    private fun startAuthStateMonitoring() {
        // Monitor Firebase Auth state - if user gets signed out (e.g., account disabled),
        // this will detect it and show appropriate message
//        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
//            if (firebaseAuth.currentUser == null) {
//                // User has been signed out (could be account disabled, token revoked, etc.)
//                showAccountDisabledModal()
//            }
//        }

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser == null && !isFinishing) {
                // Silent redirect — NOT "account disabled"
                redirectToLogin()
            }
        }


        auth.addAuthStateListener(authStateListener!!)
    }

    private fun forceLogoutDisabled() {
        stopLocationService()
        updateDriverSessionInfo(false)

        FirebaseAuth.getInstance().signOut()
        SessionPrefs.clear(this)
        prefs.edit().clear().apply()

        val intent = Intent(this, Login::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }


    private fun redirectToLogin() {
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun stopAuthStateMonitoring() {
        authStateListener?.let {
            auth.removeAuthStateListener(it)
        }
        authStateListener = null
    }

    /**
     * Periodically validates the Firebase Auth token by forcing a refresh.
     * If the account has been disabled, the token refresh will fail and trigger logout.
     * This catches cases where the user has a valid session but their account gets disabled.
     */
//    private fun startPeriodicTokenCheck() {
//        tokenCheckRunnable = object : Runnable {
//            override fun run() {
//                checkTokenValidity()
//                // Schedule next check
//                handler.postDelayed(this, TOKEN_CHECK_INTERVAL_MS)
//            }
//        }
//        // Start first check
//        handler.postDelayed(tokenCheckRunnable!!, TOKEN_CHECK_INTERVAL_MS)
//    }
//
//    private fun checkTokenCheck() {
//        tokenCheckRunnable?.let {
//            handler.removeCallbacks(it)
//        }
//        tokenCheckRunnable = null
//    }

//    private fun checkTokenValidity() {
//        val currentUser = auth.currentUser ?: return
//
//        // Force token refresh - this will fail if account is disabled
//        currentUser.getIdToken(true)
//            .addOnFailureListener { exception ->
//                // Token refresh failed - likely account disabled or token revoked
//                android.util.Log.w("MainActivity", "Token validation failed: ${exception.message}")
//
//                // Sign out and show modal
//                runOnUiThread {
//                    if (!isFinishing && !isDestroyed) {
//                        showAccountDisabledModal()
//                    }
//                }
//            }
//            .addOnSuccessListener {
//                // Token is still valid - account is active
//                android.util.Log.d("MainActivity", "Token validation successful")
//            }
//    }

    private fun showAccountDisabledModal() {
        val dialogView = layoutInflater.inflate(R.layout.custom_dialog, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.dialogTitle).text = "Session Expired"
        dialogView.findViewById<TextView>(R.id.dialogMessage).text =
            "Your session has ended. This may be because your account was disabled or your access was revoked. Please login again or contact support if you believe this is an error."
        dialogView.findViewById<View>(R.id.dialogNegativeButton).visibility = View.GONE

        val positiveButton = dialogView.findViewById<View>(R.id.dialogPositiveButton) as? Button
        positiveButton?.text = "OK"
        positiveButton?.setOnClickListener {
            dialog.dismiss()
            handleAccountDisabled()
        }

        dialog.show()
    }

    private fun handleAccountDisabled() {
        // Stop all services
        stopLocationService()
        updateDriverSessionInfo(false)

        // Clear session
        SessionPrefs.clear(this)
        prefs.edit().clear().apply()

        // Stop monitoring
        stopAuthStateMonitoring()

        // Navigate to login
        val intent = Intent(this, Login::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun signOut() {
        stopLocationService()
        updateDriverSessionInfo(false)
        auth.signOut()
        SessionPrefs.clear(this)
        startActivity(Intent(this, Login::class.java))
        finish()
    }
}
