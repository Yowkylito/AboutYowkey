@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.yowkey.portfolio.ui.sections

import kotlin.math.roundToInt

/**
 * The current time of the static hero's `hero-intro` animation (StaticHtml.heroMotion runs it on `.hero` for the
 * entrance's whole length, as its clock), or -1 when there is none to join: no static hero, an address that opened a
 * project, or no such animation, which is the case under reduced motion and without scripts. A finished animation
 * holds its end, so a long-finished intro reads as over.
 */
private fun readStaticIntro(): Double = js(
    """(function () {
        if (/^#\/projects\//.test(location.hash)) return -1;
        var hero = document.querySelector('#static-content .hero');
        if (!hero || typeof hero.getAnimations !== 'function') return -1;
        var clock = hero.getAnimations().filter(function (a) { return a.animationName === 'hero-intro'; })[0];
        return clock && clock.currentTime !== null ? Number(clock.currentTime) : -1;
    })()""",
)

internal actual fun staticIntroMillis(): Int? = readStaticIntro().takeIf { it >= 0.0 }?.roundToInt()
