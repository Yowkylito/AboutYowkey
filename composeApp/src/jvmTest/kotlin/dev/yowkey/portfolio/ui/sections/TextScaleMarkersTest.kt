package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.yowkey.portfolio.model.Experience
import dev.yowkey.portfolio.ui.components.Bullet
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import org.jetbrains.skia.Bitmap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A list's marks (a bullet's dot, a job's dot on the timeline) sit beside the first line of their text. The text grows
 * with the reader's text size (Android's font scale), so the marks must move with it and keep their place on the
 * line: the same share of its height at 1x and at 2x.
 */
class TextScaleMarkersTest {

    @Test
    fun aBulletStaysOnItsFirstLine() {
        val atOne = markShare(1f) { Bullet("A highlight") }
        val atTwo = markShare(2f) { Bullet("A highlight") }
        assertTrue(abs(atOne - atTwo) < 0.05f, "the dot's middle is $atOne of the line at 1x and $atTwo at 2x")
    }

    @Test
    fun aJobsDotStaysOnItsTitle() {
        val job = Experience(role = "Android Developer", company = "Company", period = "2024 – Present")
        val atOne = markShare(1f) { JobEntry(job, isLast = false, wide = false) }
        val atTwo = markShare(2f) { JobEntry(job, isLast = false, wide = false) }
        assertTrue(abs(atOne - atTwo) < 0.05f, "the dot's middle is $atOne of the title's line at 1x and $atTwo at 2x")
    }

    /**
     * Where the accent-coloured mark at the left edge has its middle, as a share of the first line's height (both
     * texts here are set on a 24 sp line), with the text scaled by [fontScale].
     */
    private fun markShare(fontScale: Float, content: @Composable () -> Unit): Float =
        ImageComposeScene(400, 300, Density(1f, fontScale)) {
            PortfolioTheme { Box(Modifier.background(Color.White)) { content() } }
        }.use { scene ->
            val image = Bitmap.makeFromImage(scene.render())
            val rows = (0 until image.height).filter { y -> (0 until 10).any { x -> Color(image.getColor(x, y)).isAccent() } }
            assertTrue(rows.isNotEmpty(), "no mark drawn")
            val middle = (rows.first() + rows.last() + 1) / 2f
            middle / (LINE_SP * fontScale)
        }

    /** The light theme's accent, #2563EB, and not the pale blends at a round mark's edge. */
    private fun Color.isAccent(): Boolean = blue > 0.8f && red < 0.3f && green < 0.5f

    private companion object {
        const val LINE_SP = 24f
    }
}
