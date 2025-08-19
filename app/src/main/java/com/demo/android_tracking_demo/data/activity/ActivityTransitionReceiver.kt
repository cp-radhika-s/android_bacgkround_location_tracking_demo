package com.demo.android_tracking_demo.data.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.TrackingManager
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
    lateinit var trackingManager: TrackingManager

    @Inject
    lateinit var eventRepository: EventRepository

    override fun onReceive(context: Context, intent: Intent) {
        when {
            ActivityTransitionResult.hasResult(intent) -> {
                trackingManager.handleActivityTransition(intent)
            }
            else -> {
                eventRepository.addMessage("No Activity result in intent")
            }
        }


    }
}




