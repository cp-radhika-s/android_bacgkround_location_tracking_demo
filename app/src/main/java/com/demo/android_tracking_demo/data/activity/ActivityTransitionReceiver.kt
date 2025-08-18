package com.demo.android_tracking_demo.data.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.TrackingService
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var activityRecognitionManager: ActivityRecognitionManager

    @Inject
    lateinit var eventRepository: EventRepository

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("Action=${intent.action}, extras=${intent.extras?.keySet()}")
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            result?.probableActivities?.forEach { activity ->
                val typeName = when (activity.type) {
                    DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
                    DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
                    DetectedActivity.ON_FOOT -> "ON_FOOT"
                    DetectedActivity.RUNNING -> "RUNNING"
                    DetectedActivity.WALKING -> "WALKING"
                    DetectedActivity.STILL -> "STILL"
                    DetectedActivity.TILTING -> "TILTING"
                    DetectedActivity.UNKNOWN -> "UNKNOWN"
                    else -> "OTHER"
                }
                eventRepository.addMessage(
                    "Activity: $typeName, confidence: ${activity.confidence}",
                )
            }
        } else {
            eventRepository.addMessage("No ActivityRecognitionResult in intent")
        }

        if (ActivityRecognitionResult.hasResult(intent))
            activityRecognitionManager.handleActivityUpdate(
                intent
            )
    }
}




