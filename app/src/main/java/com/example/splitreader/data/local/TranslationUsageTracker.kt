package com.example.splitreader.data.local

import android.content.Context
import com.example.splitreader.domain.model.TranslationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Characters translated in the current UTC month and the provider's monthly limit (if any). */
data class TranslationUsage(
    val charactersThisMonth: Long,
    val monthlyLimit: Long?,
)

/** Tracks per-provider monthly character usage (UTC month boundaries) for quota display. */
@Singleton
class TranslationUsageTracker @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("translation_usage", Context.MODE_PRIVATE)

    fun record(provider: TranslationProvider, characters: Int) {
        if (characters <= 0) return
        val key = monthKey(provider)
        val current = prefs.getLong(key, 0L)
        prefs.edit().putLong(key, current + characters).apply()
    }

    fun usage(provider: TranslationProvider): TranslationUsage {
        val chars = prefs.getLong(monthKey(provider), 0L)
        return TranslationUsage(chars, limitFor(provider))
    }

    fun reset(provider: TranslationProvider) {
        prefs.edit().putLong(monthKey(provider), 0L).apply()
    }

    private fun monthKey(provider: TranslationProvider): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return "${provider.name}_${year}-${"%02d".format(month)}"
    }

    private fun limitFor(provider: TranslationProvider): Long? = when (provider) {
        TranslationProvider.GOOGLE_CLOUD -> 500_000L
        TranslationProvider.DEEPL -> 500_000L
        else -> null
    }
}
