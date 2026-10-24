package dev.yowkey.portfolio.ui.sections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The hero's entrance, played once per page load: the portrait slides in from the stage's start edge to its place at
 * the end, a short soft trail behind it; as it settles, the intro reads in, one step at a time in reading order
 * ([IntroStep]), each fading in as it slides toward the end. One
 * clock in milliseconds drives all of it ([HeroMotion] is the timeline), read only in the draw and layer phases, so
 * the entrance never recomposes; the steps waiting their turn keep their space and their place in the semantics.
 *
 * Keep one per page visit, above everything that comes and goes (the list's rows, the list itself while a project is
 * open), as the page does: scrolling back to the hero or coming back from a project never plays it again, and never
 * hides a word that has shown.
 *
 * The intro waits for the portrait (its image loaded and half of it on screen) only [PORTRAIT_WAIT_MS]: with no photo
 * it reads in at once, and with a failed or slow image it goes ahead alone, the portrait fading in on its own when it
 * comes. Under reduced motion, and in previews, it starts settled: the still picture and every word at once. On the
 * web the static page plays the same entrance before the app arrives, and this starts where that one has got to.
 */
@Stable
class HeroEntrance internal constructor(startMillis: Float, private var joinsStatic: Boolean = false) {

    /** Where the entrance is on [HeroMotion]'s timeline: 0 before it starts, [HeroMotion.END_MS] once it is over. */
    internal val clock = Animatable(startMillis)

    /** False once the intro went ahead without the portrait, which then arrives on its own. */
    internal var portraitOnClock = true
        private set
    private var portraitEntered = false
    private val portraitReady = CompletableDeferred<Unit>()

    /**
     * Where the hero's name ends, in px down from the hero's top, 0 until the hero has laid it out: the bar takes the
     * name up once that has scrolled under it. Kept here, above the list, with the rest of the hero's state; it
     * changes only with the layout, never while the entrance plays.
     */
    var nameEnd by mutableIntStateOf(0)
        internal set

    /** True once the portrait has been in this page load: composed again, it is there from the first frame. */
    internal val portraitShown: Boolean get() = portraitEntered

    /** How far intro step [step] has come in, 0..1, eased. Read it in the layer phase. */
    internal fun shown(step: IntroStep, staged: Boolean = false): Float =
        EaseOutCubic.transform(HeroMotion.textProgress(clock.value, step.order(staged)))

    /**
     * The portrait is in: its image has loaded and half of it is on screen. True the first time while the clock
     * carries it (it slides in on it, or has already come in on the static page), so it takes its pose from the
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
     * Joining the static page's entrance, it keeps to the static page's clock frame by frame to the end instead: the
     * app's first frames come slowly while it starts, and a clock of its own would fall behind the page it fades in
     * over, showing the same words twice.
     */
    internal suspend fun play(reducedMotion: Boolean, hasPhoto: Boolean) {
        val end = HeroMotion.END_MS.toFloat()
        if (reducedMotion) {
            clock.snapTo(end)
            return
        }
        while (joinsStatic && clock.value < end) {
            val static = staticIntroMillis()
            if (static == null) joinsStatic = false else clock.snapTo(static.coerceIn(0, HeroMotion.END_MS).toFloat())
            if (joinsStatic && clock.value < end) withFrameNanos { }
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
 * One [HeroEntrance] for this composition's life; the page keeps one above its list. It starts settled under reduced
 * motion and in previews, and on the web where the static page has played the entrance already: part way through
 * when the app arrives mid-entrance, so it carries on from the same frame ([staticIntroMillis]).
 */
@Composable
fun rememberHeroEntrance(): HeroEntrance {
    val settled = LocalReducedMotion.current || LocalInspectionMode.current
    return remember {
        val joined = if (settled) null else staticIntroMillis()?.coerceIn(0, HeroMotion.END_MS)
        HeroEntrance(if (settled) HeroMotion.END_MS.toFloat() else joined?.toFloat() ?: 0f, joinsStatic = joined != null)
    }
}

/**
 * The intro's steps, in the order they read in. The role is a step of its own only when it stands on the stage under
 * the name ("staged", see [HeroStage]); otherwise it is part of the headline.
 */
internal enum class IntroStep {
    NAME, ROLE, HEADLINE, PROOF, ACTIONS, SUMMARY;

    /** This step's place in the reading order. */
    fun order(staged: Boolean): Int = if (staged || this == NAME) ordinal else ordinal - 1
}

/**
 * Intro step [step] of [entrance]: faded out and shifted toward the start until its turn, then in. Only the layer
 * changes, so the step keeps its space (nothing below it moves) and stays in the semantics, and its links work once
 * it shows. [staged]: the name and role stand on the stage, and the role reads in as a step of its own.
 */
internal fun Modifier.introStep(entrance: HeroEntrance, step: IntroStep, staged: Boolean = false): Modifier = graphicsLayer {
    val shown = entrance.shown(step, staged)
    alpha = shown
    translationX = -(1f - shown) * HeroMotion.TEXT_SHIFT_DP.dp.toPx()
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
