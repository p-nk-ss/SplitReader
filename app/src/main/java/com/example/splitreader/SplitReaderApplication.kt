package com.example.splitreader

import android.app.Application
import com.example.splitreader.data.bergamot.BergamotEngine
import com.example.splitreader.domain.usecase.SweepOrphanBookFilesUseCase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SplitReaderApplication : Application() {
    /** Hilt field-injects before onCreate, so onTrimMemory can never see this uninitialised. */
    @Inject lateinit var bergamotEngine: BergamotEngine
    @Inject lateinit var sweepOrphanBookFiles: SweepOrphanBookFilesUseCase

    override fun onCreate() {
        super.onCreate()
        // Only report from real (release) builds; keep developer-machine crashes out of the dashboard.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        // Once per process, off the main thread (it opens the database and lists two folders).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { sweepOrphanBookFiles() }
    }

    /** Offline HQ models are hundreds of MB of native memory; drop them when we leave the foreground. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) bergamotEngine.unloadAll()
    }
}
