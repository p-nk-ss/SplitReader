package com.example.splitreader.presentation.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/** Runs every block inline and counts them, to prove a read went through [LocalImageReadDispatcher]. */
internal class CountingDispatcher : CoroutineDispatcher() {
    @Volatile var dispatched = 0
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatched++
        block.run()
    }
}

/**
 * setContent for UI tests that show book pictures: pictures are read on the test's main thread
 * ([LocalImageReadDispatcher] = Unconfined), so their sizes are there once setContent returns and no
 * test waits on a background read. See [LocalImageReadDispatcher] for the race this removes.
 */
internal fun ComposeContentTestRule.setPictureContent(content: @Composable () -> Unit) = setContent {
    CompositionLocalProvider(LocalImageReadDispatcher provides Dispatchers.Unconfined, content = content)
}
