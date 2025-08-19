package com.demo.android_tracking_demo.ui

import android.app.Application
import com.demo.android_tracking_demo.data.domain.NotificationsHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class TrackingDemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        NotificationsHelper.createNotificationChannel(this)
    }
}