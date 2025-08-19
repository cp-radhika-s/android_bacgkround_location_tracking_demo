package com.demo.android_tracking_demo.data

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.demo.android_tracking_demo.data.location.LocationManager
import com.demo.android_tracking_demo.data.domain.EventRepository
import com.demo.android_tracking_demo.data.domain.NotificationsHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {
    @Inject
    lateinit var locationManager: LocationManager

    @Inject
    lateinit var eventRepository: EventRepository

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val startForeground = when (action) {
            ACTION_START_FG_TRACKING -> true
            ACTION_START_TRACKING -> false
            else -> true
        }

        if (startForeground) startAsForegroundService()
        locationManager.startLocationUpdates()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        eventRepository.addMessage("TrackingService onCreate")
    }

    override fun onDestroy() {
        eventRepository.addMessage("Service destroyed")
        locationManager.stopLocationUpdates()
        super.onDestroy()
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

    companion object {
        const val ACTION_START_FG_TRACKING =
            "com.demo.android_tracking_demo.action.START_FG_TRACKING"
        const val ACTION_START_TRACKING =
            "com.demo.android_tracking_demo.action.START_TRACKING"
    }
}