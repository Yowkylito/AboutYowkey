package dev.yowkey.portfolio.ui.sections

import dev.yowkey.portfolio.ui.components.shorterForms
import dev.yowkey.portfolio.ui.components.roleOf as leadRoleOf

/**
 * Whether the hero's name and role can stand on the portrait's stage, on a single column: in the tinted area at the
 * start side, left of the figure, a small gap ([GAP_TO_FIGURE_DP]) short of it. Pure, so it is unit-tested and the
 * static page's CSS is written from the same rule (`StaticHtml.heroStage`).
 *
 * The figure is drawn at the stage's full height, but never wider than [FIGURE_MAX_WIDTH] of that height: a cut-out
 * wider than that (a head-and-shoulders crop, up to about [FIGURE_ASPECT]) is drawn smaller, standing on the same
 * floor. So the block's box is fixed by the stage's size alone, whatever the photo: it is known before the photo
 * loads (on the static page, which cannot measure it, too), and nothing moves when it does.
 *
 * The name takes the largest of [NAME_SIZES_SP] that keeps every word whole on a line; under it the role, the
 * headline's lead ("Android Developer at DigiPlus since 2024"), in at most [ROLE_LINES] lines at 15sp or else 14sp,
 * whole if it fits and else the longest of its shorter forms that does (`shorterForms`). A whole role wins over a
 * bigger name or role. When even the shortest form does not fit with the name, there is no plan, and the hero keeps
 * its usual layout, the name under the stage. Lines are counted word by word, so the text is set that way too (no
 * balanced or "pretty" wrapping).
 *
 * The widths come from [TextWidth]: the app measures its own font, and so that the static page, which cannot measure,
 * reaches the same answer at every width, both take at least [SystemFontWidth]'s generous estimate.
 */
internal object HeroStage {

    /** The stage's tinted area starts this far down, so the head stands clear of it. */
    const val STAGE_TOP = 0.26f

    /**
     * The widest cut-out the stage is made for, its width over its height: a head-and-shoulders crop. A wider one
     * still fits (it is drawn smaller); the shipped photo is checked against this (SiteContentTest).
     */
    const val FIGURE_ASPECT = 1.1f

    /** The figure is never drawn wider than this share of the stage's height, nor closer to its end edge. */
    const val FIGURE_MAX_WIDTH = 0.95f
    const val FIGURE_INSET_DP = 20f

    /** The block's inset from the stage's start edge, and from the tinted area's top and bottom. */
    const val PAD_DP = 14f
    const val V_PAD_DP = 6f

    /** Kept clear between the block and the figure's box. */
    const val GAP_TO_FIGURE_DP = 10f

    val NAME_SIZES_SP = listOf(36f, 34f, 32f, 30f)
    const val NAME_LINE = 1.1f
    val ROLE_SIZES_SP = listOf(15f, 14f)
    const val ROLE_LINE = 1.34f
    const val ROLE_LINES = 2
    const val GAP_DP = 4f

    /**
     * The headline's lead role, as the bar shows it (`roleOf`), its spacing evened out so that each shorter form is
     * the start of the longer one.
     */
    fun roleOf(headline: String): String =
        leadRoleOf(headline).split(' ').filter { it.isNotEmpty() }.joinToString(" ")

    /** What stands on the stage: the name at [nameSp], and [role] at [roleSp], the whole role when [wholeRole]. */
    data class Plan(val nameSp: Float, val roleSp: Float, val role: String, val wholeRole: Boolean)

    /** The block's box on a stage [stageWidth] x [stageHeight] dp. */
    fun boxWidth(stageWidth: Float, stageHeight: Float): Float =
        stageWidth - FIGURE_INSET_DP - stageHeight * FIGURE_MAX_WIDTH - GAP_TO_FIGURE_DP - PAD_DP

    fun boxHeight(stageHeight: Float): Float = stageHeight * (1f - STAGE_TOP) - 2 * V_PAD_DP

    /**
     * The size the figure is drawn at on a stage [stageHeight] tall, for a cut-out [aspect] as wide as it is tall: the
     * full height, or less when that would be wider than [FIGURE_MAX_WIDTH] of it.
     */
    fun figureSize(stageHeight: Float, aspect: Float): Pair<Float, Float> {
        val width = minOf(stageHeight * aspect, stageHeight * FIGURE_MAX_WIDTH)
        return width to width / aspect
    }

    /** The plan for [name] and [role] on this stage at [fontScale], or null when they cannot stand on it. */
    fun plan(name: String, role: String, stageWidth: Float, stageHeight: Float, fontScale: Float, widths: TextWidth): Plan? {
        val width = boxWidth(stageWidth, stageHeight)
        val height = boxHeight(stageHeight)
        if (width <= 0f || name.isBlank() || role.isBlank()) return null
        for (form in shorterForms(role)) {
            for (roleSize in ROLE_SIZES_SP) {
                val roleLines = lines(form, roleSize * fontScale, bold = false, width, widths) ?: continue
                if (roleLines > ROLE_LINES) continue
                for (size in NAME_SIZES_SP) {
                    val nameLines = lines(name, size * fontScale, bold = true, width, widths) ?: continue
                    val used = (nameLines * size * NAME_LINE + roleLines * roleSize * ROLE_LINE) * fontScale + GAP_DP
                    if (used <= height) return Plan(size, roleSize, form, form == role.trim())
                }
            }
        }
        return null
    }

    /** The lines [text] wraps to at [width] dp, word by word, or null when a word alone is wider than that. */
    fun lines(text: String, sizeDp: Float, bold: Boolean, width: Float, widths: TextWidth): Int? {
        var lines = 0
        var line = ""
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            if (widths.width(word, sizeDp, bold) > width) return null
            val longer = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && widths.width(longer, sizeDp, bold) <= width) {
                line = longer
            } else {
                if (line.isNotEmpty() || lines == 0) lines++
                line = word
            }
        }
        return lines
    }
}

/** How wide [text] is set on one line at [sizeDp], bold (the name) or medium (the role). */
internal fun interface TextWidth {
    fun width(text: String, sizeDp: Float, bold: Boolean): Float
}

/**
 * A generous estimate of a system sans-serif's widths, for the static page, which cannot measure: each character's
 * width in ems, a little over the widest of the page's usual fonts at the sizes used. For the name (bold, 30px and
 * up) that is Arial; for the role (medium, 14 to 15px) it is SF Pro, whose text cut at those sizes sets about a tenth
 * wider than at display sizes, hence [TEXT_SIZE]. Over-estimating only ever keeps the name under the stage where it
 * might just have fitted; it never lets it run under the figure.
 */
internal object SystemFontWidth : TextWidth {
    override fun width(text: String, sizeDp: Float, bold: Boolean): Float =
        text.sumOf { em(it, bold).toDouble() }.toFloat() * sizeDp * (if (bold) 1f else TEXT_SIZE)

    private fun em(c: Char, bold: Boolean): Float = when {
        c == ' ' -> if (bold) 0.28f else 0.27f
        c in "il.,'|!:;·" -> if (bold) 0.29f else 0.24f
        c in "fjrt()-/" -> if (bold) 0.4f else 0.34f
        c in "mw" -> if (bold) 0.9f else 0.84f
        c in "MW" -> if (bold) 0.95f else 0.9f
        c.isUpperCase() -> if (bold) 0.72f else 0.68f
        c.isDigit() -> if (bold) 0.57f else 0.556f
        else -> if (bold) 0.585f else 0.555f
    }

    private const val TEXT_SIZE = 1.06f
}
