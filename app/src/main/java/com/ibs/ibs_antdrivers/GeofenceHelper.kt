package com.ibs.ibs_antdrivers.utils

import android.content.Context
import android.location.Location
import com.ibs.ibs_antdrivers.model.Geofence

class GeofenceHelper(private val context: Context) {

    /**
     * Calculate distance between two locations in meters
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val location1 = Location("point1").apply {
            latitude = lat1
            longitude = lon1
        }

        val location2 = Location("point2").apply {
            latitude = lat2
            longitude = lon2
        }

        return location1.distanceTo(location2)
    }

    /**
     * Check if a location is inside a geofence
     */
    fun isLocationInsideGeofence(location: Location, geofence: Geofence): Boolean {
        val distance = calculateDistance(
            location.latitude,
            location.longitude,
            geofence.latitude,
            geofence.longitude
        )

        return distance <= geofence.radius
    }

    /**
     * Check if a location is inside a circular geofence
     */
    fun isLocationInsideGeofence(
        locationLat: Double,
        locationLon: Double,
        geofenceLat: Double,
        geofenceLon: Double,
        radius: Float
    ): Boolean {
        val distance = calculateDistance(locationLat, locationLon, geofenceLat, geofenceLon)
        return distance <= radius
    }
}