package dev.yowkey.portfolio.ui.sections

import androidx.compose.animation.core.CubicBezierEasing

/**
 * The hero's entrance as a timeline in milliseconds from its start, pure so it is unit-tested and so the static page's
 * CSS is written from it (`StaticHtml.heroMotion`): the app and the static page play the same curves, and the app can
 * take over from the static page mid-flight without a jump. Calm on purpose, to suit a professional photo: one
 * movement and one reveal, nothing that shakes, splits or pulses.
 *
 * - 0 ms: the figure slides in from just past the stage's start edge to its place at the end over [SLIDE_MS], on a
 *   long ease-out ([SLIDE_EASE], CSS's `cubic-bezier(.22, 1, .36, 1)`): quick at first, then gliding into place, never
 *   past it. [ECHOES] faint echoes follow it, each [ECHO_LAG_MS] behind the one in front, a short soft trail that
 *   fades out from [TRAIL_FADE_AT_MS] over [TRAIL_FADE_MS], gone before the figure settles: the still picture has none.
 * - [TEXT_AT_MS], as the figure settles (within a few pixels of its place): the intro's first step reads in; each
 *   next one [TEXT_STAGGER_MS] later, each over [TEXT_MS]. With the name and role on the stage ("staged", see
 *   [HeroStage]) the role is a step of its own, after the name.
 * - [END_MS]: all of it at rest, which is the still picture.
 */
internal object HeroMotion {

    const val SLIDE_MS = 650
    val SLIDE_EASE = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    const val ECHOES = 3
    const val ECHO_LAG_MS = 24
    const val TRAIL_FADE_AT_MS = 100
    const val TRAIL_FADE_MS = 300

    const val TEXT_AT_MS = 420
    const val TEXT_STAGGER_MS = 60
    const val TEXT_MS = 400
    const val TEXT_STEPS = 5
    const val STAGED_STEPS = 6

    const val END_MS = TEXT_AT_MS + (STAGED_STEPS - 1) * TEXT_STAGGER_MS + TEXT_MS

    /** How far each intro step slides in from, toward the start. */
    const val TEXT_SHIFT_DP = 20f

    /** Where the figure is at [ms]: 0 just off the stage's start edge, 1 in its place, which it never passes. */
    fun dash(ms: Float): Float = SLIDE_EASE.transform((ms / SLIDE_MS).coerceIn(0f, 1f))

    /** How much of the trail shows at [ms]: all of it while the figure is quick, none once it has slowed to settle. */
    fun trail(ms: Float): Float = 1f - smooth(((ms - TRAIL_FADE_AT_MS) / TRAIL_FADE_MS).coerceIn(0f, 1f))

    /** When intro step [order] (its place in the reading order) starts to read in. */
    fun textAt(order: Int): Int = TEXT_AT_MS + order * TEXT_STAGGER_MS

    /** How far intro step [order] has read in at [ms], 0..1, before easing. */
    fun textProgress(ms: Float, order: Int): Float = ((ms - textAt(order)) / TEXT_MS).coerceIn(0f, 1f)

    /** Smoothstep: eases in and out, with no overshoot. */
    fun smooth(t: Float): Float = t * t * (3f - 2f * t)
}
