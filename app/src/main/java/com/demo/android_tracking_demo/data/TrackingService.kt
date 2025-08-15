package com.demo.android_tracking_demo.data

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.demo.android_tracking_demo.data.location.LocationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class TrackingService : Service() {
    private val binder = LocalBinder()

    @Inject lateinit var locationManager: LocationManager
    @Inject lateinit var eventRepository: EventRepository

    val locationFlow: StateFlow<android.location.Location?>
        get() = locationManager.locationFlow

    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("TrackingService onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("TrackingService onStartCommand")
        startAsForegroundService()
        locationManager.startLocationUpdates()
        eventRepository.addMessage("Service started")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        eventRepository.addMessage("TrackingService onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.stopLocationUpdates()
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
}