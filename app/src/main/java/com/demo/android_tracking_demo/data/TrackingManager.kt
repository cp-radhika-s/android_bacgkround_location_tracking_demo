package com.demo.android_tracking_demo.data

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import com.demo.android_tracking_demo.data.activity.ActivityRecognitionManager
import com.demo.android_tracking_demo.data.geofence.GeofenceManager
import com.demo.android_tracking_demo.data.location.LocationManager
import com.google.android.gms.location.ActivityTransitionResult
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
import com.demo.android_tracking_demo.data.domain.EventRepository
import com.demo.android_tracking_demo.data.domain.isAppInForeground
import com.google.android.gms.location.ActivityTransition

@Singleton
class TrackingManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationManager: LocationManager,
    private val eventRepository: EventRepository,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val geofenceManager: GeofenceManager
) {
    private val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stationaryTimerJob: Job? = null
    private var locationLoggingJob: Job? = null

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _trackingState = MutableStateFlow(loadPersistedState())
    val trackingState: StateFlow<TrackingState> = _trackingState

    private var lastLocation: Location? = null


    fun startTracking() {
        eventRepository.addMessage("Starting tracking")
        updateState(TrackingState.STATIONARY)
        activityRecognitionManager.start()
        plantStartGeofence()
        observeLocationUpdates()
        prefs.edit { putBoolean(KEY_IS_TRACKING, true) }
    }

    fun stopTracking() {
        eventRepository.addMessage("Stopping tracking")
        cancelStationaryTimer()
        activityRecognitionManager.stop()
        stopFgTracking()
        updateState(TrackingState.STATIONARY)
        geofenceManager.removeAll()
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
    private fun plantLastGeofence(location: Location) {
        geofenceManager.createLastGeofenceAt(location)
    }

    private fun plantStartGeofence() {
        coroutineScope.launch {
            val location = locationManager.getCurrentLocation()
            if (location == null) {
                eventRepository.addMessage("Failed to plant start geofence - location not found")
                return@launch
            }
            onLocationUpdate(location)
            geofenceManager.createStartGeofenceAt(location)
        }
    }

    fun onGeofenceExit(triggeringIds: List<String>) {
        if (triggeringIds.isNotEmpty()) {
            triggeringIds.forEach { id -> geofenceManager.removeGeofence(id) }
        }

        cancelStationaryTimer()
        updateState(TrackingState.MOVING)
        eventRepository.addMessage("GeoFence exit detected - triggeringIds $triggeringIds")
        startFgTracking()

        plantStartGeofence()
    }

    // ------------ Activity Recognition Handling ------------
    fun handleActivityTransition(intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { event ->
            eventRepository.addMessage("Activity transition: type=${event.activityType} transition=${event.transitionType}")
            if (_trackingState.value == TrackingState.MOVING
                && event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            ) {
                stationaryTimerJob?.cancel()
                stationaryTimerJob = coroutineScope.launch {
                    delay(3.minutes)
                    eventRepository.addMessage("ActivityRecognition: STILL detected")
                    updateState(TrackingState.STATIONARY)
                    stopFgTracking()
                }
            } else if (_trackingState.value == TrackingState.STATIONARY
                && event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT
            ) {
                eventRepository.addMessage("ActivityRecognition: MOVING detected")
                startFgTracking()
            } else {
                cancelStationaryTimer()
            }
        }
    }

    private fun cancelStationaryTimer() {
        stationaryTimerJob?.cancel()
        stationaryTimerJob = null
    }

    private fun startFgTracking() {
        val shouldStartForeground =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) !context.isAppInForeground() else false

        val serviceIntent = Intent(context, TrackingService::class.java).apply {
            action =
                if (shouldStartForeground) TrackingService.ACTION_START_FG_TRACKING else TrackingService.ACTION_START_TRACKING
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shouldStartForeground) {
                context.startForegroundService(serviceIntent)
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

    private fun observeLocationUpdates() {
        if (locationLoggingJob != null) return
        locationLoggingJob = coroutineScope.launch {
            locationManager.locationFlow.collectLatest { location ->
                if (location == null) return@collectLatest
                onLocationUpdate(location)
                plantLastGeofence(location)
            }
        }
    }

    private fun onLocationUpdate(location: Location) {
        val distanceMeters = lastLocation?.distanceTo(location)?.toDouble()?.toInt() ?: 0
        eventRepository.addMessage(
            "Location received - ${location.latitude}:${location.longitude} distance: $distanceMeters m",
            location.time
        )
        lastLocation = location
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
        private const val KEY_STATE = "tracking_state"
    }


    enum class TrackingState {
        MOVING,
        STATIONARY
    }

}