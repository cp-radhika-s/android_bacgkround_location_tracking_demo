package com.demo.android_tracking_demo.data

import com.demo.android_tracking_demo.data.local.TrackingEvent
import com.demo.android_tracking_demo.data.local.TrackingEventDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class EventRepository(
    private val eventDao: TrackingEventDao
) {
    private val coroutineScope = CoroutineScope(Job())

    val events: Flow<List<TrackingEvent>> = eventDao.observeAll()

    fun addMessage(message: String, timestampMs: Long = System.currentTimeMillis()) {
        coroutineScope.launch(Dispatchers.IO) {
            kotlin.runCatching {
                eventDao.insert(
                    TrackingEvent(
                        timestampMs = timestampMs,
                        message = message
                    )
                )
            }
        }
    }

    suspend fun clear() {
        eventDao.clearAll()
    }
}


