package com.example.splitreader.domain

/**
 * Reports non-fatal problems to crash monitoring. Lets domain/presentation code surface a caught
 * exception for visibility without depending on a concrete crash backend (Crashlytics). Uncaught
 * crashes are captured automatically by the backend and don't need this.
 */
interface CrashReporter {
    /** Records a handled [throwable] as a non-fatal, optionally tagged with a context [message]. */
    fun recordNonFatal(throwable: Throwable, message: String? = null)

    companion object {
        /** Reports nothing: for code built by hand (tests) where there is nothing to report to. */
        val NONE: CrashReporter = object : CrashReporter {
            override fun recordNonFatal(throwable: Throwable, message: String?) = Unit
        }
    }
}
