package com.demo.android_tracking_demo.data.activity

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import com.demo.android_tracking_demo.data.EventRepository
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

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    fun start() {
        activityRecognitionClient.requestActivityUpdates(STATIONARY_DELAY_MS, activityUpdatesPendingIntent)


        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )
        val request = ActivityTransitionRequest(transitions)
        activityRecognitionClient.requestActivityTransitionUpdates(request, activityPendingIntent)
            .addOnSuccessListener {
                eventRepository.addMessage("ActivityRecognition: listening for STILL enter/exit")
            }
            .addOnFailureListener { error ->
                eventRepository.addMessage("ActivityRecognition: failed to start - ${error.message}")
            }
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    fun stop() {
        activityRecognitionClient.removeActivityTransitionUpdates(activityPendingIntent)
            .addOnSuccessListener { eventRepository.addMessage("ActivityRecognition: stopped") }
            .addOnFailureListener { error ->
                eventRepository.addMessage("ActivityRecognition: failed to stop - ${error.message}")
            }
        activityRecognitionClient.removeActivityUpdates(activityUpdatesPendingIntent)
            .addOnSuccessListener { }
            .addOnFailureListener { }
        cancelStationaryTimer()
        stationaryListener = null
    }

    fun handleActivityTransition(intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        for (transitionEvent in result.transitionEvents) {
            if (transitionEvent.activityType == DetectedActivity.STILL) {
                if (transitionEvent.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    onStillEnter()
                } else if (transitionEvent.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                    onStillExit()
                }
            }
        }
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
        cancelStationaryTimer()
    }

    private fun cancelStationaryTimer() {
        stationaryTimerJob?.cancel()
        stationaryTimerJob = null
    }

    private val activityPendingIntent: PendingIntent by lazy {
        val intent = Intent(appContext, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            appContext,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        private const val STATIONARY_DELAY_MS = 30_000L
    }
}


