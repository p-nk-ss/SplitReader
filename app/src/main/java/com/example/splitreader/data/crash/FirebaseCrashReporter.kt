package com.example.splitreader.data.crash

import com.example.splitreader.domain.CrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/** [CrashReporter] backed by Firebase Crashlytics. Collection is gated in [SplitReaderApplication]. */
@Singleton
class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    override fun recordNonFatal(throwable: Throwable, message: String?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (message != null) crashlytics.log(message)
        crashlytics.recordException(throwable)
    }
}
