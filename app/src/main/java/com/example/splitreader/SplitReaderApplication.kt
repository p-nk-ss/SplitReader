package com.example.splitreader

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SplitReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Only report from real (release) builds; keep developer-machine crashes out of the dashboard.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
    }
}
