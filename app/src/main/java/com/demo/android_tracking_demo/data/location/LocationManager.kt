package com.demo.android_tracking_demo.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.demo.android_tracking_demo.data.domain.EventRepository
import com.demo.android_tracking_demo.data.domain.hasFineLocationPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

@Singleton
class LocationManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val eventRepository: EventRepository,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val _locationFlow = MutableStateFlow<Location?>(null)
    val locationFlow: StateFlow<Location?> = _locationFlow

    private val locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                eventRepository.addMessage("LocationManager - Location update: $location")
                _locationFlow.value = location
            }
        }

        override fun onLocationAvailability(p0: LocationAvailability) {
            super.onLocationAvailability(p0)
            eventRepository.addMessage("Location availability: ${p0.isLocationAvailable}")
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

        eventRepository.addMessage("Requesting location updates")

        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATES_INTERVAL_MS)
                .setMinUpdateIntervalMillis(LOCATION_UPDATES_INTERVAL_MS)
                .setMinUpdateDistanceMeters(DISTANCE_THRESHOLD)
                .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        ).addOnSuccessListener { location ->
            eventRepository.addMessage("Location updates started")
        }.addOnFailureListener { error ->
            eventRepository.addMessage("Failed to start location updates: ${error.message}")
        }
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        if (!appContext.hasFineLocationPermission()) {
            cont.cancel()
            return@suspendCancellableCoroutine
        }

        val tokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            tokenSource.token
        ).addOnSuccessListener { location ->
            cont.resume(location)
        }.addOnFailureListener { error ->
            cont.resumeWithException(error)
        }

        cont.invokeOnCancellation {
            tokenSource.cancel()
        }
    }

    companion object {
        private val DISTANCE_THRESHOLD = 50f // meters
        private val LOCATION_UPDATES_INTERVAL_MS = 10.seconds.inWholeMilliseconds
    }
}


