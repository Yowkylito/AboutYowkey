package dev.yowkey.portfolio.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.yowkey.portfolio.ui.theme.Motion
import dev.yowkey.portfolio.ui.theme.MotionScheme

/**
 * Which lazy rows have already entered, by item key, and the stagger slot of each new one. Hoisted to the page so
 * scrolling back never replays an entrance. Rows that start in the same frame (the first screen, a nav jump) get
 * consecutive slots, capped at [maxSlots]; a row arriving on its own (normal scrolling) gets slot 0, no delay.
 */
@Stable
class RevealTracker(
    private val batchWindowMillis: Long = 34, // two frames at 60 Hz
    private val maxSlots: Int = Motion.MaxStaggerSlots,
) {
    private val seen = HashSet<Any>()
    private var batchStart = Long.MIN_VALUE
    private var slot = 0

    fun hasSeen(key: Any): Boolean = key in seen

    /** Marks [key] seen and returns its stagger slot, 0 until [maxSlots] - 1. */
    fun reveal(key: Any, frameMillis: Long): Int {
        seen += key
        if (batchStart == Long.MIN_VALUE || frameMillis - batchStart > batchWindowMillis) {
            batchStart = frameMillis
            slot = 0
        } else {
            slot = (slot + 1).coerceAtMost(maxSlots - 1)
        }
        return slot
    }
}

/**
 * A linear 0→1 entrance progress for the lazy row keyed [key]; [entrance] eases it in the layer phase. Rows
 * already seen, or under reduced motion, start at 1. Only the layer re-runs while it animates, never composition.
 */
@Composable
fun rememberRevealProgress(key: Any, tracker: RevealTracker, motion: MotionScheme): Animatable<Float, AnimationVector1D> {
    val progress = remember(key) { Animatable(if (!motion.animatesEntrances || tracker.hasSeen(key)) 1f else 0f) }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            val slot = withFrameMillis { tracker.reveal(key, it) }
            val spec = tween<Float>(Motion.EntranceMillis, delayMillis = slot * Motion.StaggerMillis, easing = LinearEasing)
            progress.animateTo(1f, spec)
        }
    }
    return progress
}

/**
 * Fades and slides content up as [progress] goes 0→1. [lag] (0 until 1) holds this element back within the same
 * progress, so cards in one row arrive one after another without a second animation.
 */
fun Modifier.entrance(progress: () -> Float, motion: MotionScheme, lag: Float = 0f): Modifier = graphicsLayer {
    val raw = ((progress() - lag) / (1f - lag)).coerceIn(0f, 1f)
    val eased = Motion.EmphasizedDecelerate.transform(raw)
    alpha = eased
    translationY = (1f - eased) * motion.entranceOffset.toPx()
}
