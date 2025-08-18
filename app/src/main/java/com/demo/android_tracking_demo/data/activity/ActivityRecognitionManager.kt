package com.demo.android_tracking_demo.data.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.TrackingService
import com.demo.android_tracking_demo.data.hasActivityRecognitionPermission
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class ActivityRecognitionManager @Inject constructor(
    private val appContext: Context,
    private val eventRepository: EventRepository,
    private val activityRecognitionClient: ActivityRecognitionClient
) {
    private val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stationaryListener: (() -> Unit)? = null
    private var stationaryTimerJob: Job? = null

    fun registerStationaryListener(listener: () -> Unit) {
        stationaryListener = listener
    }

    @SuppressLint("MissingPermission")
    fun start() {

        if (!appContext.hasActivityRecognitionPermission()) return

        activityRecognitionClient.requestActivityUpdates(
            STATIONARY_DELAY_MS,
            activityUpdatesPendingIntent
        ).addOnSuccessListener { Timber.d("Activity updates started") }
            .addOnFailureListener { e -> Timber.d(e, "Failed to start updates") }

    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!appContext.hasActivityRecognitionPermission()) return
        activityRecognitionClient.removeActivityUpdates(activityUpdatesPendingIntent)
            .addOnSuccessListener { }
            .addOnFailureListener { }
        cancelStationaryTimer()
        stationaryListener = null
    }

    fun handleActivityUpdate(intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbable = result.mostProbableActivity
        val type = mostProbable.type
        val confidence = mostProbable.confidence
        eventRepository.addMessage("Activity update: type=$type confidence=$confidence")
        if (type == DetectedActivity.STILL) {
            onStillEnter()
        } else {
            onStillExit()
        }
    }

    private fun onStillEnter() {
        cancelStationaryTimer()
        eventRepository.addMessage("ActivityRecognition: STILL enter, scheduling 30s check")
        stationaryTimerJob = coroutineScope.launch {
            delay(STATIONARY_DELAY_MS)
            eventRepository.addMessage("USER_IS_STATIONARY")
            stationaryListener?.invoke()
        }
    }

    private fun onStillExit() {
        eventRepository.addMessage("ActivityRecognition: STILL exit, cancel timer")

        val serviceIntent = Intent(appContext, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START_ACTIVE_TRACKING
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            appContext.startForegroundService(serviceIntent)
        } else {
            appContext.startService(serviceIntent)
        }

        cancelStationaryTimer()
    }

    private fun cancelStationaryTimer() {
        stationaryTimerJob?.cancel()
        stationaryTimerJob = null
    }

    private val activityUpdatesPendingIntent: PendingIntent by lazy {
        val intent = Intent(appContext, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            appContext,
            102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val STATIONARY_DELAY_MS = 5000L
    }
}


