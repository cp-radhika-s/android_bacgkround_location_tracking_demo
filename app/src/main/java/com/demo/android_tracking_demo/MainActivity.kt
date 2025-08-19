package com.demo.android_tracking_demo

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.android_tracking_demo.data.TrackingManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var trackingManager: TrackingManager


    private var isServiceRunning by mutableStateOf<Boolean>(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
        }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasFine = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
        val hasCoarse = permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        val hasLocation = hasFine || hasCoarse

        val needsActivityRecognition = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val hasActivityRecognition = if (needsActivityRecognition) {
            permissions.getOrDefault(Manifest.permission.ACTIVITY_RECOGNITION, false)
        } else true

        if (hasLocation && hasActivityRecognition) {
            trackingManager.startTracking()
            isServiceRunning = true
        } else {
            Toast.makeText(
                this,
                "Grant location and activity recognition permissions to start.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val eventsVm: EventsViewModel = hiltViewModel()
            val eventsState = eventsVm.events.collectAsStateWithLifecycle()

            MainContent(
                serviceRunning = isServiceRunning,
                onClick = ::onStartOrStopForegroundServiceClick,
                events = eventsState.value,
                onDeleteLogs = {
                    eventsVm.deleteLogs()
                },
            )
        }

        checkAndRequestNotificationPermission()
    }

    private fun checkAndRequestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            )) {
                android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    return true
                }

                else -> {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    return false
                }
            }
        }
        return true
    }

    private fun onStartOrStopForegroundServiceClick() {
        if (!checkAndRequestNotificationPermission()) return

        if (isServiceRunning) {
            trackingManager.stopTracking()
            isServiceRunning = false
            Timber.d("Service stopped")
            return
        }

        val hasLocationPermission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

        val hasActivityRecognitionPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (hasLocationPermission && hasActivityRecognitionPermission) {
            trackingManager.startTracking()
            isServiceRunning = true
            Timber.d("Service started")
        } else {
            val permissionsToRequest = buildList {
                if (!hasLocationPermission) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (!hasActivityRecognitionPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }.toTypedArray()
            if (permissionsToRequest.isNotEmpty()) {
                locationPermissionRequest.launch(permissionsToRequest)
            }
        }
    }
}

