package com.demo.android_tracking_demo.data.di

import android.content.Context
import com.demo.android_tracking_demo.data.EventRepository
import com.demo.android_tracking_demo.data.location.LocationManager
import com.demo.android_tracking_demo.data.local.AppDatabase
import com.demo.android_tracking_demo.data.local.TrackingEventDao
import com.google.android.gms.location.LocationServices
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
    fun provideEventRepository(dao: TrackingEventDao): EventRepository = EventRepository(dao)

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context) =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideLocationManager(
        @ApplicationContext context: Context,
        repository: EventRepository,
        fusedClient: com.google.android.gms.location.FusedLocationProviderClient
    ): LocationManager = LocationManager(context, repository, fusedClient)
}


