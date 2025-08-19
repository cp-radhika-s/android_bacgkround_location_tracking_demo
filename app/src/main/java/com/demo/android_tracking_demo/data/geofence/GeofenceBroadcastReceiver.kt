package com.demo.android_tracking_demo.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.demo.android_tracking_demo.data.domain.EventRepository
import com.demo.android_tracking_demo.data.TrackingManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var trackingManager: TrackingManager
    @Inject lateinit var eventRepository: EventRepository

    override fun onReceive(context: Context, intent: Intent) {
        val parsedEvent = GeofencingEvent.fromIntent(intent)

        val event = parsedEvent ?: run {
            return
        }

        if (event.hasError()) {
            eventRepository.addMessage("Geofence error: ${event.errorCode}")
            return
        }

        val transition = event.geofenceTransition
        val triggeringIds = event.triggeringGeofences?.map { it.requestId } ?: emptyList()

        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            trackingManager.onGeofenceExit(triggeringIds)
        }
    }
}


