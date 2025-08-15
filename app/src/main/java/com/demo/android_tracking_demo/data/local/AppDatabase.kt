package com.demo.android_tracking_demo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackingEvent::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackingEventDao(): TrackingEventDao
}


