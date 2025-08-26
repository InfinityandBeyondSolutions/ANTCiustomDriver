package com.ibs.ibs_antdrivers.model

data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val altitude: Double = 0.0,
    val provider: String = ""
)

data class DeviceStatus(
    val isOnline: Boolean = false,
    val batteryLevel: Int = 0,
    val isLocationEnabled: Boolean = false,
    val isMobileDataEnabled: Boolean = false,
    val timestamp: Long = 0L
)

data class Geofence(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Float = 0f,
    val createdBy: String = "",
    val createdAt: Long = 0L
)

data class GeofenceTransition(
    val geofenceId: String = "",
    val transitionType: String = "", // ENTER or EXIT
    val timestamp: Long = 0L,
    val location: LocationData? = null
)

data class Driver(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val lastSeen: Long = 0L
)