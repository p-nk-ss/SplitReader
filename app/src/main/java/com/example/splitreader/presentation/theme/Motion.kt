package com.example.splitreader.presentation.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

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

/**
 * Modal scaffold that animates on BOTH open and close, macOS-style: the content
 * settles in with a gentle spring scale-up + fade, and — crucially — animates
 * out before the dialog actually leaves composition.
 *
 * A naive `BasicAlertDialog` removes its content the instant `onDismissRequest`
 * fires, so any exit transition never plays. Here the close path instead flips an
 * internal [MutableTransitionState]; the real [onDismiss] is invoked only once the
 * exit transition has finished. Callers drive close through the `dismiss` lambda
 * handed to [content] (e.g. a close button), and back-press / scrim taps route
 * through it automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedDialog(
    onDismiss: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    // Once the exit transition settles (idle + hidden), perform the real dismissal.
    LaunchedEffect(visible.isIdle) {
        if (visible.isIdle && !visible.targetState) onDismiss()
    }
    val dismiss: () -> Unit = { visible.targetState = false }
    BasicAlertDialog(onDismissRequest = dismiss, properties = properties) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(MotionTokens.Medium)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
            exit = fadeOut(tween(MotionTokens.Fast)) +
                scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(MotionTokens.Fast, easing = MotionTokens.EaseStandard),
                ),
        ) {
            content(dismiss)
        }
    }
}
