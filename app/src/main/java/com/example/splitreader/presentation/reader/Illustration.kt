package com.example.splitreader.presentation.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Test tag on an [Illustration]'s picture. */
internal const val ILLUSTRATION_TAG = "illustration"

private val VerticalPadding = 16.dp

/**
 * The tallest a picture may be in a pane [paneHeightPx] tall: the pane minus the picture's own
 * padding, so a portrait plate fits on one screen. Unbounded until the pane is measured.
 */
@Composable
internal fun illustrationMaxHeight(paneHeightPx: Int): Dp {
    if (paneHeightPx <= 0) return Dp.Infinity
    val pane = with(LocalDensity.current) { paneHeightPx.toDp() }
    return (pane - VerticalPadding * 2).coerceAtLeast(120.dp)
}

/**
 * A book image's size on the page, as browsers and e-readers size it: one image pixel is one dp
 * (a CSS px), at most [maxWidth] (the page between the margins) and [maxHeight] (one screen), with
 * its shape kept. Drawn at its bitmap size instead, a plate came out half size on a tablet and a
 * third or less on a phone (found on a device).
 */
internal fun illustrationSize(pixels: IntSize, maxWidth: Dp, maxHeight: Dp): DpSize {
    val ratio = pixels.width.toFloat() / pixels.height
    var width = minOf(pixels.width.dp, maxWidth)
    if (width / ratio > maxHeight) width = maxHeight * ratio
    return DpSize(width, width / ratio)
}

/** The file's own pixel size, read without decoding the picture; null when it can't be read (e.g. SVG). */
@Composable
private fun rememberImagePixels(path: String): IntSize? {
    val pixels by produceState<IntSize?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                IntSize(bounds.outWidth, bounds.outHeight).takeIf { it.width > 0 && it.height > 0 }
            }.getOrNull()
        }
    }
    return pixels
}

/** A picture's padded frame on the page; [content] fills the picture's size. Nothing for an unreadable file. */
@Composable
private fun IllustrationFrame(
    path: String,
    horizontalMargin: Float,
    maxHeight: Dp,
    content: @Composable (DpSize) -> Unit,
) {
    val pixels = rememberImagePixels(path) ?: return
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = horizontalMargin.dp, vertical = VerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        content(illustrationSize(pixels, maxWidth, maxHeight))
    }
}

/**
 * Inline illustration; spans both panes of the side-by-side layout (pictures are not translated, so
 * they are not split). Its space is laid out as soon as the file's size is read, before the picture
 * decodes, so the row doesn't grow under a jump. The bitmap is decoded off the main thread,
 * downscaled to about the screen width against OOM on large plates. An unreadable file (missing,
 * SVG) draws nothing, keeping the reader robust.
 * [horizontalMargin]: the page margin in dp (`ReadingAppearance.horizontalMargin`), as the text's.
 * [maxHeight]: see [illustrationMaxHeight].
 */
@Composable
internal fun Illustration(path: String, horizontalMargin: Float, maxHeight: Dp) {
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }.toInt().coerceAtLeast(1)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > screenWidthPx * 2) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    IllustrationFrame(path, horizontalMargin, maxHeight) { size ->
        Box(Modifier.size(size).clip(RoundedCornerShape(4.dp)).testTag(ILLUSTRATION_TAG)) {
            bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
    }
}

/** The empty space an [Illustration] takes — the stacked layout's translation pane, which shows no pictures. */
@Composable
internal fun IllustrationSpace(path: String, horizontalMargin: Float, maxHeight: Dp) {
    IllustrationFrame(path, horizontalMargin, maxHeight) { size -> Box(Modifier.size(size)) }
}
