package com.example.splitreader.presentation.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Motion design tokens, mirroring the Spacing/Radii token objects. The app uses a
 * custom CompositionLocal theme (not M3 motionScheme), so durations/easing live
 * here and every animation references them instead of scattering raw literals.
 * Editorial reading app → keep motion subtle and quick.
 */
object MotionTokens {
    const val Fast = 160     // selection feedback, color fades
    const val Medium = 240   // screen/content transitions, dialog entrance
    const val Slow = 360     // empty-state reveal

    // M3 "standard" easing curve.
    val EaseStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * Smoothly interpolates a selection color (selected vs. unselected). Drop-in
 * replacement for a raw `if (selected) a else b` color so the swap fades.
 */
@Composable
fun animatedSelection(target: Color, label: String): Color =
    animateColorAsState(
        targetValue = target,
        animationSpec = tween(MotionTokens.Fast, easing = MotionTokens.EaseStandard),
        label = label,
    ).value

/** Fades its content in once, the first time it enters composition. */
@Composable
fun FadeInOnAppear(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(tween(MotionTokens.Slow)),
        exit = fadeOut(),
    ) { content() }
}
