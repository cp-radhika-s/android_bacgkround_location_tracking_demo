package com.demo.android_tracking_demo.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var trackingManager: TrackingManager
    @Inject lateinit var eventRepository: EventRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            eventRepository.addMessage("BOOT_COMPLETED received; attempting resume")
            trackingManager.resumeIfPreviouslyTracking()
        }
    }
}


