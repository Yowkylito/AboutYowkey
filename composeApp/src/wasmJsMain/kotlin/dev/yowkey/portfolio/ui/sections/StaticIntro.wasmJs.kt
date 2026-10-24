@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.yowkey.portfolio.ui.sections

import kotlin.math.roundToInt

/**
 * How long ago the static hero's `hero-intro` animation started (StaticHtml.heroMotion runs it on `.hero` for the
 * entrance's whole length, as its clock), or -1 when there is none to join: no static hero, an address that opened a
 * project, or no such animation, which is the case under reduced motion and without scripts. Measured from its start
 * time with `performance.now()` rather than read as its current time, which only moves on the page's frames and so
 * stands still while the app's script holds the page (as it does while the app first starts): the app would join
 * behind. Not started yet (its first frame is still to come) reads as 0.
 */
private fun readStaticIntro(): Double = js(
    """(function () {
        if (/^#\/projects\//.test(location.hash)) return -1;
        var hero = document.querySelector('#static-content .hero');
        if (!hero || typeof hero.getAnimations !== 'function') return -1;
        var clock = hero.getAnimations().filter(function (a) { return a.animationName === 'hero-intro'; })[0];
        if (!clock) return -1;
        return clock.startTime === null ? 0 : Math.max(0, performance.now() - Number(clock.startTime));
    })()""",
)

internal actual fun staticIntroMillis(): Int? = readStaticIntro().takeIf { it >= 0.0 }?.roundToInt()
