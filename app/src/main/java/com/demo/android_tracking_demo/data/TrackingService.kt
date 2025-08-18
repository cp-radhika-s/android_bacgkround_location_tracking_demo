package com.demo.android_tracking_demo.data

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.demo.android_tracking_demo.data.location.LocationManager
import com.demo.android_tracking_demo.data.activity.ActivityRecognitionManager
import com.demo.android_tracking_demo.data.geofence.GeofenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class TrackingService : Service() {
    @Inject
    lateinit var locationManager: LocationManager

    @Inject
    lateinit var eventRepository: EventRepository

    @Inject
    lateinit var activityRecognitionManager: ActivityRecognitionManager

    @Inject
    lateinit var geofenceManager: GeofenceManager

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("TrackingService onBind")
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("TrackingService onStartCommand action=${intent?.action}")
        startAsForegroundService()
        when (intent?.action) {
            ACTION_START_ACTIVE_TRACKING -> {
                eventRepository.addMessage("Starting active tracking with fused provider")
                locationManager.startLocationUpdates()
            }
            else -> {
                eventRepository.addMessage("Service started: waiting for activity state")
                activityRecognitionManager.registerStationaryListener {
                    eventRepository.addMessage("State: IDLE -> STATIONARY; fetching last known location")
                    tryCreateStationaryGeofence()
                }
                activityRecognitionManager.start()
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        eventRepository.addMessage("TrackingService onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopLocationUpdates()
        activityRecognitionManager.stop()
        eventRepository.addMessage("Service destroyed")
    }

    private fun startAsForegroundService() {
        NotificationsHelper.createNotificationChannel(this)

        ServiceCompat.startForeground(
            this,
            1,
            NotificationsHelper.buildNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
        )
    }

    fun stopForegroundService() {
        stopSelf()
    }

    private fun tryCreateStationaryGeofence() {
        locationManager.getLastKnownLocation { location ->
            if (location == null) {
                eventRepository.addMessage("Cannot create geofence: last location is null")
                return@getLastKnownLocation
            }
            geofenceManager.createGeofenceAt(location)
            eventRepository.addMessage("Stationary geofence planted; stopping service")
            stopForegroundService()
        }
    }

    companion object {
        const val ACTION_START_ACTIVE_TRACKING = "com.demo.android_tracking_demo.action.START_ACTIVE_TRACKING"
    }
}