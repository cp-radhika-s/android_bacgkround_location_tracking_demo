package com.demo.android_tracking_demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.local.TrackingEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventsViewModel(application: Application) : AndroidViewModel(application) {


    private val repository: EventRepository by lazy {
        (getApplication() as TrackingDemoApp).eventRepository
    }

    val events: StateFlow<List<TrackingEvent>> = repository.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteLogs() = viewModelScope.launch {
        repository.clear();
    }
}


