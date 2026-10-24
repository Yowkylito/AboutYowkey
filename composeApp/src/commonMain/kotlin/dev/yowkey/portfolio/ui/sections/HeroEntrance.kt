package dev.yowkey.portfolio.ui.sections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The hero's entrance, played once per page load: the portrait dashes in from the stage's start edge to its place at
 * the end, its echo trail streaming out behind it, and lands with its scream as the impact; from the landing on, the
 * intro reads in, one step at a time in reading order ([IntroStep]), each fading in as it slides toward the end. One
 * clock in milliseconds drives all of it ([HeroMotion] is the timeline), read only in the draw and layer phases, so
 * the entrance never recomposes; the steps waiting their turn keep their space and their place in the semantics.
 *
 * Keep one per page visit, above everything that comes and goes (the list's rows, the list itself while a project is
 * open), as the page does: scrolling back to the hero or coming back from a project never plays it again, and never
 * hides a word that has shown.
 *
 * The intro waits for the portrait (its image loaded and half of it on screen) only [PORTRAIT_WAIT_MS]: with no photo
 * it reads in at once, and with a failed or slow image it goes ahead alone, the portrait fading in on its own when it
 * comes. Under reduced motion, and in previews, it starts settled: the still picture and every word at once.
 */
@Stable
class HeroEntrance internal constructor(startMillis: Float) {

    /** Where the entrance is on [HeroMotion]'s timeline: 0 before it starts, [HeroMotion.END_MS] once it is over. */
    internal val clock = Animatable(startMillis)

    /** False once the intro went ahead without the portrait, which then arrives on its own. */
    internal var portraitOnClock = true
        private set
    private var portraitEntered = false
    private val portraitReady = CompletableDeferred<Unit>()

    /** True while the clock moves the portrait: it ignores gestures and returns to view, and goes where it is put. */
    internal val movesPortrait: Boolean get() = portraitOnClock && clock.value < HeroMotion.END_MS

    /** How far intro step [step] has come in, 0..1, eased. Read it in the layer phase. */
    internal fun shown(step: Int): Float = EaseOutCubic.transform(HeroMotion.textProgress(clock.value, step))

    /**
     * The portrait is in: its image has loaded and half of it is on screen. True the first time while the clock
     * carries it (it dashes in on it, or has already come in on the static page), so it takes its pose from the
     * clock; false after that, and when the intro went ahead without it, so it makes its own arrival.
     */
    internal fun portraitArrives(): Boolean {
        if (portraitEntered) return false
        portraitEntered = true
        if (!portraitOnClock) return false
        portraitReady.complete(Unit)
        return true
    }

    /**
     * Runs the clock to the end from wherever it is, so a hero composed again mid-entrance carries on. From the start
     * it first waits for the portrait, at most [PORTRAIT_WAIT_MS]; without one in time the intro goes ahead alone.
     */
    internal suspend fun play(reducedMotion: Boolean, hasPhoto: Boolean) {
        val end = HeroMotion.END_MS.toFloat()
        if (reducedMotion) {
            clock.snapTo(end)
            return
        }
        if (clock.value >= end) return
        if (clock.value == 0f) {
            val inTime = hasPhoto && withTimeoutOrNull(PORTRAIT_WAIT_MS) { portraitReady.await() } != null
            if (!inTime) {
                portraitOnClock = false
                clock.snapTo(HeroMotion.TEXT_AT_MS.toFloat())
            }
        }
        clock.animateTo(end, tween((end - clock.value).roundToInt(), easing = LinearEasing))
    }

    companion object {
        /** How long the intro waits for the portrait before it reads in without it. */
        const val PORTRAIT_WAIT_MS = 500L
    }
}

/**
 * One [HeroEntrance] for this composition's life, settled from the start under reduced motion and in previews; the
 * page keeps one above its list.
 */
@Composable
fun rememberHeroEntrance(): HeroEntrance {
    val settled = LocalReducedMotion.current || LocalInspectionMode.current
    return remember { HeroEntrance(if (settled) HeroMotion.END_MS.toFloat() else 0f) }
}

/** The intro's steps, in the order they read in. */
internal object IntroStep {
    const val NAME = 0
    const val HEADLINE = 1
    const val PROOF = 2
    const val ACTIONS = 3
    const val SUMMARY = 4
}

/**
 * Intro step [step] of [entrance]: faded out and shifted toward the start until its turn, then in. Only the layer
 * changes, so the step keeps its space (nothing below it moves) and stays in the semantics, and its links work once
 * it shows.
 */
internal fun Modifier.introStep(entrance: HeroEntrance, step: Int): Modifier = graphicsLayer {
    val shown = entrance.shown(step)
    alpha = shown
    translationX = -(1f - shown) * HeroMotion.TEXT_SHIFT_DP.dp.toPx()
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
