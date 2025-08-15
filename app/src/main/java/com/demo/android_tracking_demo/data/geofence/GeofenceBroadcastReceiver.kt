package com.demo.android_tracking_demo.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.demo.android_tracking_demo.data.EventRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var eventRepository: EventRepository

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            eventRepository.addMessage("Geofence error: ${event.errorCode}", System.currentTimeMillis())
            return
        }

        val transition = event.geofenceTransition
        val transitionLabel = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            else -> "UNKNOWN"
        }

        val ids = event.triggeringGeofences?.joinToString { it.requestId } ?: "none"
        eventRepository.addMessage("Geofence transition: $transitionLabel ids=$ids", System.currentTimeMillis())
    }
}


