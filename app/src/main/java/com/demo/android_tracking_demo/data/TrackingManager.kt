package com.demo.android_tracking_demo.data

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import androidx.core.content.edit
import timber.log.Timber

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
    private var locationLoggingJob: Job? = null

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _trackingState = MutableStateFlow(loadPersistedState())
    val trackingState: StateFlow<TrackingState> = _trackingState

    private var lastLocation: Location? = null


    fun startTracking() {
        eventRepository.addMessage("Starting tracking")
        updateState(TrackingState.MOVING)
        activityRecognitionManager.start()
        plantGeoFence()
        startLocationDistanceLogging()
        startFgTracking()
        prefs.edit { putBoolean(KEY_IS_TRACKING, true) }
    }

    fun stopTracking() {
        eventRepository.addMessage("Stopping tracking")
        cancelStationaryTimer()
        activityRecognitionManager.stop()
        locationManager.stopLocationUpdates()
        stopFgTracking()
        updateState(TrackingState.STATIONARY)
        geofenceManager.removeGeofence()
        prefs.edit { putBoolean(KEY_IS_TRACKING, false) }
    }

    fun resumeIfPreviouslyTracking(): Boolean {
        val wasTracking = prefs.getBoolean(KEY_IS_TRACKING, false)
        if (wasTracking) {
            startTracking()
        }
        return wasTracking
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
        geofenceManager.removeGeofence()
        updateState(TrackingState.MOVING)
        eventRepository.addMessage("GeoFence exit detected, starting active tracking")
        startFgTracking()
    }

    // ------------ Activity Recognition Handling ------------
    fun handleActivityUpdate(intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbable = result.mostProbableActivity
        val type = mostProbable.type
        val confidence = mostProbable.confidence
        eventRepository.addMessage("Activity update: type=$type confidence=$confidence")
        if (confidence < 50 || type == DetectedActivity.UNKNOWN) return

        val timeSinceStart =
            System.currentTimeMillis() - result.time

        Timber.d("XXX Activity update: timeSinceStart=$timeSinceStart elapsedRealtimeMillis ${result.time}")

        if (type == DetectedActivity.STILL) {
            onStillEnter(result.time)
        } else {
            onStillExit()
        }
    }

    private fun onStillEnter(detectedAt: Long) {
        if (_trackingState.value == TrackingState.STATIONARY) {
            return
        }
        eventRepository.addMessage("ActivityRecognition: STILL enter, scheduling 3m check")

        stationaryTimerJob?.cancel()
        val timeSinceStart =
            System.currentTimeMillis() - detectedAt

        if (timeSinceStart >= 180_000) {
            updateState(TrackingState.STATIONARY)
            plantGeoFence()
            stopFgTracking()
            return
        }


        stationaryTimerJob = coroutineScope.launch {
            val delay = 180_000 - timeSinceStart
            delay(delay)
            updateState(TrackingState.STATIONARY)
            plantGeoFence()
            stopFgTracking()
        }
    }

    private fun onStillExit() {
        if (_trackingState.value == TrackingState.MOVING) return

        eventRepository.addMessage("ActivityRecognition: STILL exit")
        updateState(TrackingState.MOVING)
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.isAppInForeground()) {
                    locationManager.startLocationUpdates()
                } else {
                    context.startForegroundService(serviceIntent)
                }
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            eventRepository.addMessage("Failed to start tracking service: ${e.message}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                plantGeoFence()
            }
        }
    }

    private fun stopFgTracking() {
        context.stopService(Intent(context, TrackingService::class.java))
    }

    private fun startLocationDistanceLogging() {
        if (locationLoggingJob != null) return
        locationLoggingJob = coroutineScope.launch {
            locationManager.locationFlow.collectLatest { location ->
                if (location == null) return@collectLatest
                val distanceMeters = lastLocation?.distanceTo(location) ?: 0f
                eventRepository.addMessage(
                    "Location received - ${location.latitude}:${location.longitude} distance: ${
                        distanceMeters.toDouble().toInt()
                    }m",
                    location.time
                )
                lastLocation = location
            }
        }
    }


    private fun updateState(newState: TrackingState) {
        if (_trackingState.value == newState) return
        _trackingState.value = newState
        prefs.edit { putString(KEY_STATE, newState.name) }
        eventRepository.addMessage("State changed to $newState")
    }

    private fun loadPersistedState(): TrackingState {
        val stored = prefs.getString(KEY_STATE, TrackingState.MOVING.name)
        return runCatching { TrackingState.valueOf(stored!!) }.getOrDefault(TrackingState.MOVING)
    }

    companion object {
        private const val PREFS_NAME = "tracking_prefs"
        private const val KEY_IS_TRACKING = "is_tracking"
        private const val KEY_STATE = "state"
    }


    enum class TrackingState {
        MOVING,
        STATIONARY
    }

}