package dev.yowkey.portfolio.ui.image

import androidx.compose.runtime.Immutable
import kotlin.math.min

/**
 * Sizes for one project's carousel, in dp, from the carousel's width and its screenshots' aspect ratios. Pure, so
 * it is unit-tested and answers intrinsic-size queries without composing anything.
 *
 * The slide height comes from the tallest screenshot, so a project mixing portrait and landscape shots never
 * changes height while paging. Phone-only projects size slides by height, so the neighbours peek in on both sides;
 * anything with a landscape shot gives the centre slide most of the width.
 */
@Immutable
data class CarouselGeometry(val pageWidth: Float, val slideHeight: Float) {

    /** Height of the pager itself: the slide plus room for the glow and the neighbours' tilt. */
    val pagerHeight: Float get() = slideHeight + 2 * GlowPadding

    /** The framed size of a screenshot of [aspect] inside one page: fills the width, or the height if taller. */
    fun frameSize(aspect: Float): Pair<Float, Float> {
        val height = framedHeight(aspect, pageWidth)
        return if (height <= slideHeight + 0.5f) pageWidth to height else framedWidth(aspect, slideHeight) to slideHeight
    }

    companion object {
        const val PhoneBezel = 6f
        const val BrowserBar = 22f
        const val MaxSlideHeight = 360f
        const val PageSpacing = 12f
        const val GlowPadding = 20f
        const val MixedHeightRatio = 1.5f

        fun of(width: Float, aspects: List<Float>): CarouselGeometry {
            require(aspects.isNotEmpty()) { "a carousel needs at least one screenshot" }
            val allPortrait = aspects.all { it < 1f }
            val share = when {
                aspects.size == 1 -> if (allPortrait) 0.5f else 1f
                allPortrait -> 0.46f
                else -> 0.9f
            }
            val target = width * share
            // With a landscape shot in the set, phones may stand at most 1.5x its height: sized by the phones
            // alone, the landscape slide floats small in a tall, mostly empty slot.
            val landscapeCap = aspects.filter { it >= 1f }.maxOfOrNull { framedHeight(it, target) * MixedHeightRatio }
            val height = minOf(aspects.maxOf { framedHeight(it, target) }, MaxSlideHeight, landscapeCap ?: MaxSlideHeight)
            val page = if (allPortrait) {
                aspects.maxOf { framedWidth(it, height) }
            } else {
                min(target, aspects.filter { it >= 1f }.maxOf { framedWidth(it, height) })
            }
            return CarouselGeometry(pageWidth = page, slideHeight = height)
        }

        /** Phones get a bezel on every side; landscape shots get a browser bar on top. */
        fun framedHeight(aspect: Float, width: Float): Float =
            if (aspect < 1f) (width - 2 * PhoneBezel) / aspect + 2 * PhoneBezel else width / aspect + BrowserBar

        fun framedWidth(aspect: Float, height: Float): Float =
            if (aspect < 1f) (height - 2 * PhoneBezel) * aspect + 2 * PhoneBezel else (height - BrowserBar) * aspect
    }
}
