package com.demo.android_tracking_demo.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.hasFineLocationPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class LocationManager @Inject constructor(
    private val appContext: Context,
    private val eventRepository: EventRepository,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val _locationFlow = MutableStateFlow<Location?>(null)
    val locationFlow: StateFlow<Location?> = _locationFlow

    private val locationCallback: LocationCallback = object : LocationCallback() {
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

    fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val request = LocationRequest.Builder(LOCATION_UPDATES_INTERVAL_MS).build()
        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(onResult: (Location?) -> Unit) {
        if (!appContext.hasFineLocationPermission()) {
            onResult(null)
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    eventRepository.addMessage(
                        "Last known location: ${location.latitude}-${location.longitude}",
                        location.time
                    )
                } else {
                    eventRepository.addMessage("Last known location: null")
                }
                onResult(location)
            }
            .addOnFailureListener { error ->
                eventRepository.addMessage("Failed to get last known location: ${error.message}")
                onResult(null)
            }
    }

    companion object {
        private val LOCATION_UPDATES_INTERVAL_MS = 1.seconds.inWholeMilliseconds
    }
}


