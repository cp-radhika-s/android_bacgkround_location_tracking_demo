package com.demo.android_tracking_demo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: TrackingEvent)

    @Query("SELECT * FROM tracking_events ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<TrackingEvent>>

    @Query("DELETE FROM tracking_events")
    suspend fun clearAll()
}


