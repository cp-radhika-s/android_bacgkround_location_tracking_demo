package com.demo.android_tracking_demo.data.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.geofence.GeofenceBroadcastReceiver
import com.demo.android_tracking_demo.data.hasFineLocationPermission
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    private val appContext: Context,
    private val eventRepository: EventRepository,
    private val geofencingClient: GeofencingClient
) {
    @SuppressLint("MissingPermission")
    fun createGeofenceAt(
        location: Location,
    ) {
        if (!appContext.hasFineLocationPermission()) return

        val geofence = Geofence.Builder()
            .setRequestId(DEFAULT_REQUEST_ID)
            .setCircularRegion(
                location.latitude,
                location.longitude,
                DEFAULT_GEOFENCE_RADIUS_METERS
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(DEFAULT_TRANSITIONS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(request, geofencePendingIntent).addOnSuccessListener {
            eventRepository.addMessage(
                "Geofence created at ${location.latitude},${location.longitude} radius=${DEFAULT_GEOFENCE_RADIUS_METERS}m",
                System.currentTimeMillis()
            )
        }.addOnFailureListener { error ->
            eventRepository.addMessage(
                "Failed to create geofence: ${error.message}",
                System.currentTimeMillis()
            )
        }
    }

    fun removeGeofence(requestId: String = DEFAULT_REQUEST_ID) {
        geofencingClient.removeGeofences(listOf(requestId)).addOnSuccessListener {
            eventRepository.addMessage(
                "Geofence removed: $requestId",
                System.currentTimeMillis()
            )
        }.addOnFailureListener { error ->
            eventRepository.addMessage(
                "Failed to remove geofence: ${error.message}",
                System.currentTimeMillis()
            )
        }
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val DEFAULT_REQUEST_ID = "stationary_geofence"
        const val DEFAULT_GEOFENCE_RADIUS_METERS = 100f
        const val DEFAULT_TRANSITIONS = Geofence.GEOFENCE_TRANSITION_EXIT
    }
}


