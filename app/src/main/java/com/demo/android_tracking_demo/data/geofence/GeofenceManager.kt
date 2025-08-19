package com.demo.android_tracking_demo.data.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.geofence.GeofenceBroadcastReceiver
import com.demo.android_tracking_demo.data.hasFineLocationPermission
import com.demo.android_tracking_demo.data.hasBackgroundLocationPermission
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class GeofenceManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val eventRepository: EventRepository,
    private val geofencingClient: GeofencingClient
) {

    @SuppressLint("MissingPermission")
    fun createStartGeofenceAt(location: Location) {
        upsertGeofenceAt(location, START_GEOFENCE_ID)
    }

    @SuppressLint("MissingPermission")
    fun createLastGeofenceAt(location: Location) {
        upsertGeofenceAt(location, LAST_GEOFENCE_ID)
    }

    @SuppressLint("MissingPermission")
    private fun upsertGeofenceAt(location: Location, requestId: String) {
        if (!appContext.hasFineLocationPermission()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !appContext.hasBackgroundLocationPermission()) {
            eventRepository.addMessage("Warning: ACCESS_BACKGROUND_LOCATION not granted; geofence events may not fire in background")
        }

        val geofence = Geofence.Builder()
            .setRequestId(requestId)
            .setCircularRegion(
                location.latitude,
                location.longitude,
                DEFAULT_GEOFENCE_RADIUS_METERS
            )
            .setNotificationResponsiveness(30_000)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(DEFAULT_TRANSITIONS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
            .addGeofence(geofence)
            .build()

        geofencingClient.removeGeofences(listOf(requestId)).addOnCompleteListener {
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnFailureListener { error ->
                eventRepository.addMessage(
                    "Failed to create geofence [$requestId]: ${error.message}",
                )
            }
        }
    }

    fun removeGeofence(requestId: String = DEFAULT_REQUEST_ID) {
        geofencingClient.removeGeofences(listOf(requestId))
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )
        PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            flags
        )
    }

    companion object {
        const val DEFAULT_REQUEST_ID = "stationary_geofence"
        const val START_GEOFENCE_ID = "start_region"
        const val LAST_GEOFENCE_ID = "last_region"
        const val DEFAULT_GEOFENCE_RADIUS_METERS = 100f
        const val DEFAULT_TRANSITIONS = Geofence.GEOFENCE_TRANSITION_EXIT
    }
}


