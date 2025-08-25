package com.demo.android_tracking_demo.data.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import com.demo.android_tracking_demo.data.domain.EventRepository
import com.demo.android_tracking_demo.data.domain.hasFineLocationPermission
import com.demo.android_tracking_demo.data.domain.hasBackgroundLocationPermission
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
                100f
            )
            .setNotificationResponsiveness(0)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
            .addGeofence(geofence)
            .build()

        geofencingClient.removeGeofences(listOf(requestId)).addOnCompleteListener {
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    eventRepository.addMessage("Geofence [$requestId] created successfully - ${location.latitude}, ${location.longitude}")
                }
                .addOnFailureListener { error ->
                    eventRepository.addMessage(
                        "Failed to create geofence [$requestId]: ${error.message}",
                    )
                }
        }
    }

    fun removeGeofence(requestId: String = DEFAULT_REQUEST_ID) {
        eventRepository.addMessage("Removing geofence with ID: $requestId")
        geofencingClient.removeGeofences(listOf(requestId))
    }

    fun removeAll() {
        removeGeofence(START_GEOFENCE_ID)
        removeGeofence(LAST_GEOFENCE_ID)
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(appContext, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            appContext,
            103,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        const val DEFAULT_REQUEST_ID = "stationary_geofence"
        const val START_GEOFENCE_ID = "start_region"
        const val LAST_GEOFENCE_ID = "last_region"
    }
}


