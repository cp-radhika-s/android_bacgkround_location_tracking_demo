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
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @param:ApplicationContext private val appContext: Context,
    private val activityRecognitionClient: ActivityRecognitionClient
) {
    @SuppressLint("MissingPermission")
    fun start() {

        if (!appContext.hasActivityRecognitionPermission()) return

        Timber.d("Activity updates requested")
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
    }


    private val activityUpdatesPendingIntent: PendingIntent by lazy {
        val intent = Intent(appContext, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            appContext,
            102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        private const val STATIONARY_DELAY_MS = 5000L
    }
}


