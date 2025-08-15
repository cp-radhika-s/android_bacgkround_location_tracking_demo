package com.demo.android_tracking_demo.data.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransitionResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

	@Inject lateinit var activityRecognitionManager: ActivityRecognitionManager

	override fun onReceive(context: Context, intent: Intent) {
		when {
			ActivityTransitionResult.hasResult(intent) -> activityRecognitionManager.handleActivityTransition(intent)
			ActivityRecognitionResult.hasResult(intent) -> activityRecognitionManager.handleActivityUpdate(intent)
		}
	}
}


