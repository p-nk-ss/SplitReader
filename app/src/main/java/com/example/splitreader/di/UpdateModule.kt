package com.example.splitreader.di

import android.content.Context
import com.example.splitreader.data.update.PlayAppUpdates
import com.example.splitreader.domain.repository.AppUpdates
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    @Provides
    @Singleton
    fun providePlayAppUpdates(@ApplicationContext context: Context): PlayAppUpdates =
        PlayAppUpdates(
            manager = AppUpdateManagerFactory.create(context),
            prefs = context.getSharedPreferences(PlayAppUpdates.PREFS, Context.MODE_PRIVATE),
        )

    @Provides
    fun provideAppUpdates(impl: PlayAppUpdates): AppUpdates = impl
}
