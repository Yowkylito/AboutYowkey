package dev.yowkey.portfolio.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every duration, easing and distance the UI animates with. Values follow Material 3's motion tokens; the
 * longest single animation (an entrance plus its capped stagger) stays under 650 ms.
 */
object Motion {
    /** Entrances: fast start, long soft landing. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Exits: gentle start, quick finish, so leaving never lingers. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** State changes that stay on screen: hover, press, the top bar's elevation. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val ShortMillis = 120
    const val MediumMillis = 220
    const val LongMillis = 320
    const val EntranceMillis = 480

    /** Delay between rows that enter together; capped so a fast scroll never queues long animations. */
    const val StaggerMillis = 60
    const val MaxStaggerSlots = 4

    const val AutoplayMillis = 4_000L

    val EntranceOffset: Dp = 20.dp
    val HoverLift: Dp = 3.dp
    val HoverElevation: Dp = 10.dp
    const val PressScale = 0.97f
}

/**
 * The motion the current user gets. [reduced] mirrors `prefers-reduced-motion`: nothing translates or scales,
 * and fades are short or instant. Read it through [LocalMotion].
 */
@Immutable
class MotionScheme(val reduced: Boolean) {
    val entranceOffset: Dp = if (reduced) 0.dp else Motion.EntranceOffset
    val hoverLift: Dp = if (reduced) 0.dp else Motion.HoverLift
    val pressScale: Float = if (reduced) 1f else Motion.PressScale
    val animatesEntrances: Boolean = !reduced
    val autoplays: Boolean = !reduced

    /** Hover, press and other reversible state changes. */
    fun <T> state(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(Motion.MediumMillis, easing = Motion.Standard)

    /** Overlays and dialogs coming in; a short plain fade under reduced motion. */
    fun <T> enter(): FiniteAnimationSpec<T> =
        tween(if (reduced) Motion.ShortMillis else Motion.LongMillis, easing = if (reduced) Motion.Standard else Motion.EmphasizedDecelerate)

    fun <T> exit(): FiniteAnimationSpec<T> =
        tween(if (reduced) Motion.ShortMillis else Motion.MediumMillis, easing = if (reduced) Motion.Standard else Motion.EmphasizedAccelerate)

    /** Paging a carousel or the viewer. */
    fun <T> page(): AnimationSpec<T> = if (reduced) snap() else tween(Motion.LongMillis + 80, easing = Motion.Standard)

    companion object {
        val Default = MotionScheme(reduced = false)
        val Reduced = MotionScheme(reduced = true)
    }
}

val LocalMotion = staticCompositionLocalOf { MotionScheme.Default }
