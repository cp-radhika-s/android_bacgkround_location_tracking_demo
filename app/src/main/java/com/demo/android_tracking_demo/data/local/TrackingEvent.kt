package com.demo.android_tracking_demo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_events")
data class TrackingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val message: String,
)


