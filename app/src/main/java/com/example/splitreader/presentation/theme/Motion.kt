package com.example.splitreader.presentation.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
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

    // Spring specs for physics-based motion. Springs settle more organically than a
    // fixed-duration tween, so they read as "smoother/more alive" — used for press
    // feedback, scale-ins and content-size changes. Color fades stay on tween
    // (a color spring can over/undershoot the hue).
    /** Gentle settle for scale-ins / entrances. Mirrors the AnimatedDialog spring. */
    val SpringStandard: SpringSpec<Float> =
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    /** Quicker, slightly springier response for tap/press feedback. */
    val SpringSnappy: SpringSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
    /** Soft, low-stiffness settle for content-size / layout changes. */
    val SpringGentle: SpringSpec<Float> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)
    /** Gentle settle for slide offsets (enter transitions move an [IntOffset]). */
    val SpringSlide: SpringSpec<IntOffset> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow, visibilityThreshold = IntOffset.VisibilityThreshold)

    /** Per-item delay (ms) for staggered list entrances. */
    const val StaggerStep = 28
    /** Cap the stagger so long lists don't take forever to fully reveal. */
    const val StaggerMaxItems = 8
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

/** Fades + lifts its content in once, the first time it enters composition. */
@Composable
fun FadeInOnAppear(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(tween(MotionTokens.Slow)) +
            slideInVertically(animationSpec = MotionTokens.SpringSlide) { it / 16 },
        exit = fadeOut(),
    ) { content() }
}

/**
 * Fades + lifts content in with a per-[index] delay, for first-load list/grid
 * reveals. Combine with `Modifier.animateItem()` on the list item itself — that
 * keeps handling add/remove/reorder while this handles the one-shot entrance.
 */
@Composable
fun StaggeredAppear(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    val delay = index.coerceAtMost(MotionTokens.StaggerMaxItems) * MotionTokens.StaggerStep
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(tween(MotionTokens.Medium, delayMillis = delay)) +
            slideInVertically(
                animationSpec = tween(
                    MotionTokens.Medium,
                    delayMillis = delay,
                    easing = MotionTokens.EaseStandard,
                ),
            ) { it / 6 },
        exit = fadeOut(tween(MotionTokens.Fast)),
    ) { content() }
}

/**
 * Tactile press feedback: gently scales its element down while pressed and springs
 * back on release. Transform-only (graphicsLayer) so there is no layout cost.
 *
 * Pass the SAME [interactionSource] you hand to `clickable(...)` so the scale tracks
 * the real press state:
 * ```
 * val interaction = remember { MutableInteractionSource() }
 * Modifier.pressScale(interaction).clickable(interactionSource = interaction, indication = …) { … }
 * ```
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MotionTokens.SpringSnappy,
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Shimmering skeleton fill that works in every reader palette (pulls tones from
 * [LocalReaderPalette]). Apply to a sized [Box] — or use [ShimmerBox] — to stand in
 * for content that is still loading instead of a bare spinner.
 */
fun Modifier.shimmer(shape: Shape = RectangleShape): Modifier = composed {
    val palette = LocalReaderPalette.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val base = palette.bg3
    // A subtle tint toward ink guarantees a visible highlight in light AND dark themes.
    val highlight = lerp(palette.bg3, palette.ink, 0.07f)
    this
        .clip(shape)
        .drawWithCache {
            val widthPx = size.width
            // Sweep a soft diagonal band from off-screen left to off-screen right.
            val startX = progress * (widthPx * 2f) - widthPx
            val brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(startX, 0f),
                end = Offset(startX + widthPx, size.height),
            )
            onDrawBehind { drawRect(brush) }
        }
}

/** A ready-made shimmering placeholder block. Caller sizes it via [modifier]. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
    Box(modifier.shimmer(shape))
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
