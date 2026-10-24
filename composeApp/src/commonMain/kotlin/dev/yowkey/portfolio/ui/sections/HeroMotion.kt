package dev.yowkey.portfolio.ui.sections

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The hero's entrance as a timeline in milliseconds from its start, pure so it is unit-tested and so the static page's
 * CSS is written from it (`StaticHtml.heroMotion`): the app and the static page play the same curves, and the app can
 * take over from the static page mid-flight without a jump.
 *
 * - 0 ms: the figure leaves from just past the stage's start edge, thrown toward its place at the end: a damped
 *   spring with a running start, so it is fastest at once and slows all the way in, half the way there in about
 *   100 ms. It lands at about 345 ms, runs on about 3% past its place and settles back by [DASH_MS]. Each echo of the
 *   trail follows the same path [ECHO_LAG_MS] behind the one in front of it, so the trail stretches with the speed
 *   and closes up as the figure stops.
 * - [IMPACT_AT_MS]: the landing's swell, timed so its attack peaks as the figure lands: the figure shakes and its
 *   colour splits, and the trail bursts out and ripples, each echo [RIPPLE_STEP_MS] after the one in front of it.
 * - [TEXT_AT_MS]: the intro's first step reads in; each next one [TEXT_STAGGER_MS] later, each over [TEXT_MS].
 * - [END_MS]: all of it at rest, which is the still picture.
 */
internal object HeroMotion {

    const val DASH_MS = 900
    const val ECHOES = 7
    const val ECHO_LAG_MS = 28
    const val IMPACT_AT_MS = 330
    const val IMPACT_MS = 700
    const val RIPPLE_STEP_MS = 35
    const val TEXT_AT_MS = 360
    const val TEXT_STAGGER_MS = 75
    const val TEXT_MS = 450
    const val TEXT_STEPS = 5
    const val END_MS = TEXT_AT_MS + (TEXT_STEPS - 1) * TEXT_STAGGER_MS + TEXT_MS

    /** How far each intro step slides in from, toward the start. */
    const val TEXT_SHIFT_DP = 20f

    /** The scream's figure: how far it shakes, and how far apart its colours split. */
    const val SHAKE_DP = 3f
    const val SPLIT_DP = 6f

    /** How much farther out the trail reaches at the swell's height, as a share of its reach at rest. */
    const val BURST = 0.6f

    /**
     * How much wider an echo is drawn at the dash's top speed, stretched back along its path, and how much fainter:
     * smeared thinner, so the trail streams rather than piling up in a block of colour.
     */
    const val SMEAR = 0.6f
    const val THIN = 0.5f

    /** The scream's swell rises over this share of its length, then falls away. */
    const val ATTACK = 0.1f

    /**
     * Where the figure is at [ms]: 0 is just off the stage's start edge, 1 is its place; a little over 1 while it runs
     * on past it. Exactly 0 before the start and 1 from [DASH_MS].
     */
    fun dash(ms: Float): Float = when {
        ms <= 0f -> 0f
        ms >= DASH_MS -> 1f
        else -> 1f - spring(ms / 1000f).first
    }

    /** How fast the figure moves on toward its place at [ms], as a share of its fastest (its start); 0 going back. */
    fun speed(ms: Float): Float =
        if (ms <= 0f || ms >= DASH_MS) 0f else (-spring(ms / 1000f).second / LAUNCH).coerceIn(0f, 1f)

    /**
     * The scream's loudness at [t] of its length: a hard attack to full over the first [ATTACK], then a long fall
     * back to silence. 0 before it starts and once it is over.
     */
    fun swell(t: Float): Float = when {
        t <= 0f || t >= 1f -> 0f
        t < ATTACK -> 1f - (1f - t / ATTACK).pow(2)
        else -> (1f - (t - ATTACK) / (1f - ATTACK)).pow(2)
    }

    /**
     * The swell of a scream [total] ms long, [ms] into it, as echo [echo] of the trail hears it (0 is the figure):
     * each echo a ripple step after the one in front of it, so the burst runs out along the trail, and every one of
     * them quiet again by [total].
     */
    fun swellAt(ms: Float, total: Int, echo: Int): Float =
        swell((ms - echo * RIPPLE_STEP_MS) / (total - ECHOES * RIPPLE_STEP_MS))

    /** The landing's swell at [ms] of the entrance, as echo [echo] hears it (0 is the figure). */
    fun landing(ms: Float, echo: Int = 0): Float = swellAt(ms - IMPACT_AT_MS, IMPACT_MS, echo)

    /** How far through the landing the entrance is at [ms], 0..1: the trail's waves roll on with it. */
    fun landingProgress(ms: Float): Float = ((ms - IMPACT_AT_MS) / IMPACT_MS).coerceIn(0f, 1f)

    /** How far intro step [step] has read in at [ms], 0..1, before easing. */
    fun textProgress(ms: Float, step: Int): Float =
        ((ms - TEXT_AT_MS - step * TEXT_STAGGER_MS) / TEXT_MS).coerceIn(0f, 1f)

    /**
     * The figure's shake at [seconds] into a scream, as a share of [SHAKE_DP] at full swell: two sines, sideways
     * faster than up and down, so it buzzes rather than sways.
     */
    fun shake(seconds: Float): Pair<Float, Float> =
        sin(seconds * 2f * PI.toFloat() * 21f) to 0.5f * sin(seconds * 2f * PI.toFloat() * 15f + 1f)

    /**
     * How far the figure still has to go at [seconds], in its travel (1 at the start), and how fast that shrinks,
     * per second: an underdamped spring released at [LAUNCH] toward its place.
     */
    private fun spring(seconds: Float): Pair<Float, Float> {
        val decay = DAMPING * STIFFNESS
        val frequency = STIFFNESS * sqrt(1f - DAMPING * DAMPING)
        val b = (decay - LAUNCH) / frequency
        val e = exp(-decay * seconds)
        val c = cos(frequency * seconds)
        val s = sin(frequency * seconds)
        val left = e * (c + b * s)
        val rate = -decay * left + e * (-frequency * s + b * frequency * c)
        return left to rate
    }

    /** Damping ratio and natural frequency (rad/s) of the dash's spring, and the speed it starts at (travels/s). */
    private const val DAMPING = 0.8f
    private const val STIFFNESS = 8f
    private const val LAUNCH = 6f
}
