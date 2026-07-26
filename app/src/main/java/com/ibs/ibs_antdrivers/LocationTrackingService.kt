package com.ibs.ibs_antdrivers.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.*
import com.google.firebase.database.*
import com.google.firebase.auth.FirebaseAuth
import com.ibs.ibs_antdrivers.R
import com.ibs.ibs_antdrivers.model.LocationData
import com.ibs.ibs_antdrivers.model.DeviceStatus

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var database: DatabaseReference

    private val handler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updateDeviceStatus()
            handler.postDelayed(this, STATUS_UPDATE_INTERVAL)
        }
    }

    companion object {
        const val ACTION_START = "com.ibs.ibs_antdrivers.action.START_LOCATION_TRACKING"
        const val ACTION_STOP = "com.ibs.ibs_antdrivers.action.STOP_LOCATION_TRACKING"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val LOCATION_UPDATE_INTERVAL = 30000L // 30 seconds
        private const val STATUS_UPDATE_INTERVAL = 60000L // 1 minute
        private const val TAG = "LocationTracking"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        database = FirebaseDatabase.getInstance().reference

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createLocationRequest()
        createLocationCallback()
        createNotificationChannel()

        // Start status monitoring
        handler.post(statusUpdateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                START_NOT_STICKY
            }

            // A sticky service is restarted with a null intent. Treat that restart
            // exactly like an explicit start so it is promoted within the system deadline.
            ACTION_START, null -> startTrackingOrStop(startId)

            else -> {
                Log.w(TAG, "Ignoring unsupported service action: ${intent.action}")
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }



    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        handler.removeCallbacks(statusUpdateRunnable)
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_UPDATE_INTERVAL / 2)
            setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
        }.build()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                locationResult.lastLocation?.let { location ->
                    sendLocationToFirebase(location)
                    checkGeofences(location)
                }
            }
        }
    }

    private fun startTrackingOrStop(startId: Int): Int {
        if (!hasLocationPermission() || !isLocationEnabled()) {
            Log.w(TAG, "Location tracking not started: permission or location services unavailable")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return try {
            val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(),
                foregroundType
            )
            startLocationUpdates()
            START_STICKY
        } catch (e: SecurityException) {
            // Android 14+ validates location foreground-service prerequisites at runtime.
            // A revoked permission or disabled location must not crash the app process.
            Log.e(TAG, "Unable to start location foreground service", e)
            stopSelf(startId)
            START_NOT_STICKY
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Permission is checked immediately before this method is called and again here
        // to handle runtime revocation between service starts.
        if (!hasLocationPermission()) return

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun sendLocationToFirebase(location: Location) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val timestamp = System.currentTimeMillis()

        val locationData = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = timestamp,
            speed = if (location.hasSpeed()) location.speed else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            provider = location.provider ?: "unknown"
        )

        // Store in real-time location
        database.child("drivers")
            .child(userId)
            .child("currentLocation")
            .setValue(locationData)

        // Store in location history
        database.child("drivers")
            .child(userId)
            .child("locationHistory")
            .child(timestamp.toString())
            .setValue(locationData)
    }

    private fun checkGeofences(location: Location) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Get geofences from Firebase
        database.child("geofences").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (geofenceSnapshot in snapshot.children) {
                    val geofenceId = geofenceSnapshot.key ?: continue
                    val lat = geofenceSnapshot.child("latitude").getValue(Double::class.java) ?: continue
                    val lng = geofenceSnapshot.child("longitude").getValue(Double::class.java) ?: continue
                    val radius = geofenceSnapshot.child("radius").getValue(Float::class.java) ?: continue

                    val geofenceLocation = Location("geofence").apply {
                        latitude = lat
                        longitude = lng
                    }

                    val distance = location.distanceTo(geofenceLocation)
                    val isInside = distance <= radius

                    // Check previous state
                    checkGeofenceTransition(userId, geofenceId, isInside)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun checkGeofenceTransition(userId: String, geofenceId: String, isCurrentlyInside: Boolean) {
        val geofenceStateRef = database.child("drivers")
            .child(userId)
            .child("geofenceStates")
            .child(geofenceId)

        geofenceStateRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val wasInside = snapshot.getValue(Boolean::class.java) ?: false

                if (wasInside != isCurrentlyInside) {
                    // State changed - log the transition
                    val timestamp = System.currentTimeMillis()
                    val transitionType = if (isCurrentlyInside) "ENTER" else "EXIT"

                    val transitionData = mapOf(
                        "geofenceId" to geofenceId,
                        "transitionType" to transitionType,
                        "timestamp" to timestamp
                    )

                    database.child("drivers")
                        .child(userId)
                        .child("geofenceTransitions")
                        .child(timestamp.toString())
                        .setValue(transitionData)

                    // Update current state
                    geofenceStateRef.setValue(isCurrentlyInside)
                    if (isCurrentlyInside) {
                        // On ENTER: set currentGeofence
                        database.child("drivers")
                            .child(userId)
                            .child("currentGeofence")
                            .setValue(mapOf(
                                "geofenceId" to geofenceId,
                                "enteredAt" to System.currentTimeMillis()
                            ))
                    } else {
                        // On EXIT: remove currentGeofence if it matches
                        database.child("drivers")
                            .child(userId)
                            .child("currentGeofence")
                            .removeValue()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateDeviceStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val timestamp = System.currentTimeMillis()

        val deviceStatus = DeviceStatus(
            isOnline = true,
            batteryLevel = getBatteryLevel(),
            isLocationEnabled = isLocationEnabled(),
            isMobileDataEnabled = isMobileDataEnabled(),
            timestamp = timestamp
        )

        database.child("drivers")
            .child(userId)
            .child("deviceStatus")
            .setValue(deviceStatus)
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isMobileDataEnabled(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            return info != null && info.isConnected && info.type == android.net.ConnectivityManager.TYPE_MOBILE
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks location in background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ace Nut Traders")
            .setContentText("Location tracking is active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Using system icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
