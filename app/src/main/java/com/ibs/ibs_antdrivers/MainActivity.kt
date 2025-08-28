package com.ibs.ibs_antdrivers

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.service.LocationTrackingService

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            permissions.entries.forEach { (permission, granted) ->
                if (!granted) {
                    Toast.makeText(this, "$permission denied. Some features may be limited.", Toast.LENGTH_LONG).show()
                }
            }
        }
        sharedPreferences.edit().putBoolean("permissions_requested", true).apply()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false
        } else true

        if (fineLocationGranted && coarseLocationGranted && backgroundLocationGranted) {
            startLocationService()
        } else {
            Toast.makeText(this, "Location permissions are required for tracking", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the layout
        setContentView(R.layout.activity_main)

        // Initialize auth and preferences
        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Check if user is logged in
        if (auth.currentUser == null) {
            startActivity(Intent(this, Login::class.java))
            finish()
            return
        }

        // Set up BottomNavigationView
        val navView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Link BottomNavigationView with NavController
        navView.setupWithNavController(navController)

        // Setup location services
        checkPermissionsAndStartService()

        // Request permissions on first login
        if (!sharedPreferences.getBoolean("permissions_requested", false)) {
            requestAllPermissions()
        }

        // Stop ringtone if playing
        try {
            val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(this, notificationSound)
            if (ringtone?.isPlaying == true) {
                ringtone.stop()
            }
        } catch (e: Exception) {
            // Handle ringtone error silently
        }

        // Show status message
        Toast.makeText(this, "Meerkat Tracking Service Started", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        // Check if user is signed in
        if (auth.currentUser == null) {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }

    private fun checkPermissionsAndStartService() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startLocationService()
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        Toast.makeText(this, "Location tracking is now active", Toast.LENGTH_SHORT).show()

        // Update driver status in Firebase
        updateDriverStatus(true)

        // Minimize the app
        moveTaskToBack(true)
    }

    private fun updateDriverStatus(isActive: Boolean) {
        auth.currentUser?.let { user ->
            val database = FirebaseDatabase.getInstance().reference
            val driverData = mapOf(
                "id" to user.uid,
                "email" to user.email,
                "name" to (user.displayName ?: user.email ?: "Unknown"),
                "isActive" to isActive,
                "lastSeen" to System.currentTimeMillis()
            )

            database.child("drivers")
                .child(user.uid)
                .child("info")
                .updateChildren(driverData)
                .addOnSuccessListener {
                    // Successfully updated status
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed to update status: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun signOut() {
        // Stop location service
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        stopService(serviceIntent)

        // Update driver status
        updateDriverStatus(false)

        // Sign out from Firebase
        auth.signOut()

        // Navigate to login
        startActivity(Intent(this, Login::class.java))
        finish()
    }

    private fun requestAllPermissions() {
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            sharedPreferences.edit().putBoolean("permissions_requested", true).apply()
        }
    }
}