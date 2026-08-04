package com.example.splitreader.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.ReadingDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round-trip and, more importantly, **key isolation** for the two stacked-reader preferences.
 *
 * The plausible defect in plumbing like this is not arithmetic, it is a copy-pasted string: writing
 * the vertical ratio into `"split_ratio"` would make the two preferences one, silently coupling the
 * landscape and stacked layouts. Nothing else in the suite would notice — both values are floats in
 * the same range, so every clamp test still passes and the reader still renders.
 *
 * `ReadingProgressManager` needs only a `Context`, so this runs under Robolectric with the real
 * `SharedPreferences` rather than a fake, which is what makes the key names actually observable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingProgressManagerSplitPrefsTest {

    private lateinit var manager: ReadingProgressManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("reading_progress", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        manager = ReadingProgressManager(context)
    }

    @Test
    fun `unset preferences fall back to the declared defaults`() {
        assertEquals(ReadingDefaults.VERTICAL_SPLIT_RATIO, manager.getVerticalSplitRatio(), 1e-6f)
        assertFalse(manager.getPortraitHintDismissed())
    }

    @Test
    fun `the vertical ratio round-trips`() {
        manager.saveVerticalSplitRatio(0.65f)
        assertEquals(0.65f, manager.getVerticalSplitRatio(), 1e-6f)
    }

    @Test
    fun `the portrait hint flag round-trips`() {
        manager.savePortraitHintDismissed(true)
        assertTrue(manager.getPortraitHintDismissed())
    }

    /**
     * The one that earns its keep: writing the vertical ratio must not disturb the horizontal one.
     * A shared key makes this fail with the horizontal getter returning 0.65 instead of its default.
     */
    @Test
    fun `saving the vertical ratio leaves the horizontal one untouched`() {
        manager.saveVerticalSplitRatio(0.65f)

        assertEquals(
            "The horizontal ratio moved when only the vertical one was written — the two " +
                "preferences are sharing a SharedPreferences key.",
            ReadingDefaults.SPLIT_RATIO, manager.getSplitRatio(), 1e-6f,
        )
    }

    @Test
    fun `saving the horizontal ratio leaves the vertical one untouched`() {
        manager.saveSplitRatio(0.35f)

        assertEquals(
            "The vertical ratio moved when only the horizontal one was written — the two " +
                "preferences are sharing a SharedPreferences key.",
            ReadingDefaults.VERTICAL_SPLIT_RATIO, manager.getVerticalSplitRatio(), 1e-6f,
        )
    }

    /** Both directions at once, so a swap of the two keys cannot pass by cancelling itself out. */
    @Test
    fun `the two ratios hold independent values simultaneously`() {
        manager.saveSplitRatio(0.35f)
        manager.saveVerticalSplitRatio(0.65f)

        assertEquals(0.35f, manager.getSplitRatio(), 1e-6f)
        assertEquals(0.65f, manager.getVerticalSplitRatio(), 1e-6f)
    }
}
