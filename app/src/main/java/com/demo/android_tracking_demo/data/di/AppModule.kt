package com.demo.android_tracking_demo.data.di

import android.content.Context
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.location.LocationManager
import com.demo.android_tracking_demo.data.geofence.GeofenceManager
import com.demo.android_tracking_demo.data.activity.ActivityRecognitionManager
import com.demo.android_tracking_demo.data.local.AppDatabase
import com.demo.android_tracking_demo.data.local.TrackingEventDao
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.room.Room

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tracking_demo.db"
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideTrackingEventDao(db: AppDatabase): TrackingEventDao = db.trackingEventDao()

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context) =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideGeofencingClient(@ApplicationContext context: Context): GeofencingClient =
        LocationServices.getGeofencingClient(context)

    @Provides
    @Singleton
    fun provideActivityRecognitionClient(@ApplicationContext context: Context): ActivityRecognitionClient =
        ActivityRecognition.getClient(context)

}


