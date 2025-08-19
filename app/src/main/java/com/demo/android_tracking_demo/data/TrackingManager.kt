package com.demo.android_tracking_demo.data

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.SystemClock
import com.demo.android_tracking_demo.data.activity.ActivityRecognitionManager
import com.demo.android_tracking_demo.data.geofence.GeofenceManager
import com.demo.android_tracking_demo.data.location.LocationManager
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.ActivityTransition
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
        geofenceManager.removeGeofence(GeofenceManager.START_GEOFENCE_ID)
        geofenceManager.removeGeofence(GeofenceManager.LAST_GEOFENCE_ID)
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
    private fun plantStartGeofence() {
        coroutineScope.launch {
            locationManager.getCurrentLocation().collectLatest { location ->
                if (location != null) {
                    geofenceManager.createStartGeofenceAt(location)
                }
            }
        }
    }

    private fun plantLastGeofence(location: Location) {
        geofenceManager.createLastGeofenceAt(location)
    }

    fun onGeofenceExit(triggeringIds: List<String>) {
        if (triggeringIds.isNotEmpty()) {
            triggeringIds.forEach { id -> geofenceManager.removeGeofence(id) }
        }

        updateState(TrackingState.MOVING)
        eventRepository.addMessage("GeoFence exit detected, starting active tracking")
        startFgTracking()

        lastLocation?.let { current ->
            geofenceManager.createStartGeofenceAt(current)
        } ?: plantStartGeofence()
    }

    // ------------ Activity Recognition Handling ------------
    fun handleActivityTransition(intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { event ->
            val activityType = event.activityType
            val transitionType = event.transitionType
            eventRepository.addMessage("Activity transition: type=$activityType transition=$transitionType")

            if (activityType == DetectedActivity.STILL) {
                if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    onStillEnter()
                } else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                    onStillExit()
                }
            }
        }
    }

    private fun onStillEnter() {
        if (_trackingState.value == TrackingState.STATIONARY) {
            return
        }
        eventRepository.addMessage("ActivityRecognition: STILL enter, scheduling 3m check")

        stationaryTimerJob?.cancel()
        stationaryTimerJob = coroutineScope.launch {
            delay(3.minutes)
            updateState(TrackingState.STATIONARY)
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
                plantStartGeofence()
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
                if (_trackingState.value == TrackingState.MOVING) {
                    plantLastGeofence(location)
                }
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