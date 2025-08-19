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
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
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
                _locationFlow.value = location
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

        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATES_INTERVAL_MS)
                .setMinUpdateIntervalMillis(LOCATION_UPDATES_INTERVAL_MS)
                .setMinUpdateDistanceMeters(DISTANCE_THRESHOLD)
                .setWaitForAccurateLocation(true)
                .build()
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
    fun getCurrentLocation(): Flow<Location?> = callbackFlow {
        if (!appContext.hasFineLocationPermission()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val tokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            tokenSource.token
        ).addOnSuccessListener { location: Location? ->
            if (location == null) {
                eventRepository.addMessage("Last known location: null")
            }
            trySend(location).isSuccess
            close()
        }.addOnFailureListener { error ->
            eventRepository.addMessage("Failed to get last known location: ${error.message}")
            trySend(null).isSuccess
            close(error)
        }

        awaitClose {
            tokenSource.cancel()
        }
    }

    companion object {
        private val DISTANCE_THRESHOLD = 50f // meters
        private val LOCATION_UPDATES_INTERVAL_MS = 10.seconds.inWholeMilliseconds
    }
}


