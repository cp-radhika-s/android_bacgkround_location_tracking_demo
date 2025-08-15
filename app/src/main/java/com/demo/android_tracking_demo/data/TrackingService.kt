package com.demo.android_tracking_demo.data

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import com.demo.android_tracking_demo.TrackingDemoApp
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds


class TrackingService : Service() {
    private val binder = LocalBinder()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var timerJob: Job? = null

    private lateinit var eventRepository: EventRepository

    private val _locationFlow = MutableStateFlow<Location?>(null)
    var locationFlow: StateFlow<Location?> = _locationFlow

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
        startLocationUpdates()
        eventRepository.addMessage("Service started")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        eventRepository = (application as TrackingDemoApp).eventRepository
        eventRepository.addMessage("TrackingService onCreate")
        setupLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()
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

    private fun setupLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    _locationFlow.value = location

                    eventRepository.addMessage(
                        "Location update: ${location.latitude}-${location.longitude}",
                        location.time
                    )
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.requestLocationUpdates(
            LocationRequest.Builder(
                LOCATION_UPDATES_INTERVAL_MS
            ).build(), locationCallback, Looper.getMainLooper()
        )
    }

    companion object {
        private val LOCATION_UPDATES_INTERVAL_MS = 1.seconds.inWholeMilliseconds
    }
}