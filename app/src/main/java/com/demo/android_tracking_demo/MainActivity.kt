package com.demo.android_tracking_demo

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.demo.android_tracking_demo.data.TrackingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.hilt.navigation.compose.hiltViewModel
import com.demo.android_tracking_demo.data.location.LocationManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var locationManager: LocationManager
    private var isServiceRunning by mutableStateOf<Boolean>(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
        }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                startForegroundService()
            }

            permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                startForegroundService()
            }

            else -> {
                Toast.makeText(this, "Location permission is required!", Toast.LENGTH_SHORT).show()
            }
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
        if (!checkAndRequestNotificationPermission()) {
            locationPermissionRequest.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            if (isServiceRunning) {
                stopService(Intent(this, TrackingService::class.java))
                isServiceRunning = false
                Timber.d("Service stopped")
            } else {
                startForegroundService()
                isServiceRunning = true
                Timber.d("Service started")
            }
        }
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, TrackingService::class.java))
        }
    }
}

