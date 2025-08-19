package com.demo.android_tracking_demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.demo.android_tracking_demo.ui.theme.Android_tracking_demoTheme
import com.demo.android_tracking_demo.data.domain.local.TrackingEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MainContent(
    serviceRunning: Boolean,
    onClick: () -> Unit,
    onDeleteLogs: () -> Unit,
    events: List<TrackingEvent> = emptyList(),
    stateLabel: String,
) {
    Android_tracking_demoTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ForegroundServiceSampleScreenContent(
                serviceRunning = serviceRunning,
                onClick = onClick,
                events = events,
                onDeleteLogs = onDeleteLogs,
                stateLabel = stateLabel,
            )
        }
    }
}

@Composable
private fun ForegroundServiceSampleScreenContent(
    serviceRunning: Boolean,
    onClick: () -> Unit,
    events: List<TrackingEvent>,
    onDeleteLogs: () -> Unit,
    stateLabel: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onClick) {
                Text(
                    text = if (serviceRunning) "Stop Service" else "Start Service",
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "State: $stateLabel", color = Color.Gray)
            IconButton(onClick = onDeleteLogs) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Logs")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        EventsList(events = events)
    }
}

@Composable
private fun EventsList(events: List<TrackingEvent>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        items(events) { event ->
            val formattedTime = rememberFormattedTime(event.timestampMs)
            Text(
                text = "$formattedTime: ${event.message}",
                modifier = Modifier.padding(vertical = 8.dp)
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun rememberFormattedTime(timestampMs: Long): String {
    val formatter = SimpleDateFormat("dd/MM hh:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}