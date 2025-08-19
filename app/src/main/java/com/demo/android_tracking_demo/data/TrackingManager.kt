package com.demo.android_tracking_demo.data

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION
import com.demo.android_tracking_demo.data.activity.ActivityRecognitionManager
import com.demo.android_tracking_demo.data.geofence.GeofenceManager
import com.demo.android_tracking_demo.data.location.LocationManager
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class TrackingManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationManager: LocationManager,
    private val eventRepository: EventRepository,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val geofenceManager: GeofenceManager
) {
    private val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stationaryTimerJob: Job? = null


    fun startTracking() {
        eventRepository.addMessage("Starting tracking")
        locationManager.startLocationUpdates()
        activityRecognitionManager.start()
    }

    fun stopTracking() {
        eventRepository.addMessage("Stopping tracking")
        activityRecognitionManager.stop()
        locationManager.stopLocationUpdates()
        stopFgTracking()
        cancelStationaryTimer()
    }

    // ------------ GeoFence Handling ------------
    private fun plantGeoFence() {
        coroutineScope.launch {
            locationManager.getCurrentLocation().collectLatest { location ->
                if (location != null) {
                    geofenceManager.removeGeofence()
                    geofenceManager.createGeofenceAt(location)

                }
            }
        }
    }

    fun onGeoFenceExit() {
        cancelStationaryTimer()
        geofenceManager.removeGeofence()
        eventRepository.addMessage("GeoFence exit detected, stopping tracking")
        startFgTracking()
    }

    // ------------ Activity Recognition Handling ------------
    fun handleActivityUpdate(intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbable = result.mostProbableActivity
        val type = mostProbable.type
        val confidence = mostProbable.confidence
        eventRepository.addMessage("Activity update: type=$type confidence=$confidence")
        if (confidence < 50) return
        if (type == DetectedActivity.STILL) {
            onStillEnter()
        } else {
            onStillExit()
        }
    }

    private fun onStillEnter() {
        cancelStationaryTimer()
        eventRepository.addMessage("ActivityRecognition: STILL enter, scheduling 3m check")
        stationaryTimerJob = coroutineScope.launch {
            delay(3.minutes)
            eventRepository.addMessage("USER_IS_STATIONARY")
            plantGeoFence()
            stopFgTracking()
        }
    }

    private fun onStillExit() {
        eventRepository.addMessage("ActivityRecognition: STILL exit, cancel timer")
        startFgTracking()
        cancelStationaryTimer()
    }

    private fun cancelStationaryTimer() {
        stationaryTimerJob?.cancel()
        stationaryTimerJob = null
    }

    private fun startFgTracking() {
        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_ACTIVE_TRACKING
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                eventRepository.addMessage("Failed to start foreground service: ${e.message}")
                if (VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    plantGeoFence()
                }
            }
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopFgTracking() {
        context.stopService(Intent(context, TrackingService::class.java))
    }
}