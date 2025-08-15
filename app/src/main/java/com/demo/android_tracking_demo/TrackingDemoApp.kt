package com.demo.android_tracking_demo

import android.app.Application
import com.demo.android_tracking_demo.data.NotificationsHelper
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.local.AppDatabase
import timber.log.Timber

class TrackingDemoApp : Application() {
    lateinit var eventRepository: EventRepository
        private set

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        NotificationsHelper.createNotificationChannel(this)
        val db = AppDatabase.getInstance(this)
        eventRepository = EventRepository(db.trackingEventDao())
    }

}