package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.ui.FeedbackUiState
import dev.yowkey.portfolio.ui.MessageUiState
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import org.jetbrains.skia.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nothing but its edge shows where an empty text field or an unlit star of the rating is, so WCAG 1.4.11 asks 3:1 of
 * that edge against what it sits on, the forms' dialog, in both themes; and each star, a control of its own, is a
 * 48 dp target. Measured on the real forms: the fields found through their semantics, their left border read off the
 * rendered pixels.
 */
class FormControlsTest {

    @Test
    fun theFieldsShowTheirEdgesAt3To1InBothThemes() {
        for (dark in listOf(false, true)) {
            forms(dark) { image, surface, nodes ->
                val fields = nodes.filter { SemanticsProperties.EditableText in it.config }
                assertEquals(6, fields.size, "the note form's three fields and the message form's three")
                fields.forEach { field ->
                    val edge = strongest(image, surface, field.boundsInRoot.run { Rect(left - 1, center.y, left + 3, center.y + 1) })
                    val ratio = contrast(edge, surface)
                    assertTrue(ratio >= 3.0, "dark=$dark: a field's border is ${"%.2f".format(ratio)}:1 on the dialog")
                }
            }
        }
    }

    @Test
    fun theUnlitStarsShowAt3To1InBothThemes() {
        for (dark in listOf(false, true)) {
            forms(dark) { image, surface, nodes ->
                val stars = (1..5).map { star -> nodes.single { it.label == FeedbackCopy.ratingLabel(star) } }
                stars.forEach { star ->
                    val ratio = contrast(strongest(image, surface, star.boundsInRoot), surface)
                    assertTrue(ratio >= 3.0, "dark=$dark: ${star.label} is ${"%.2f".format(ratio)}:1 on the dialog")
                }
            }
        }
    }

    @Test
    fun eachStarIsA48dpTarget() {
        forms(dark = false) { _, _, nodes ->
            (1..5).forEach { star ->
                val bounds = nodes.single { it.label == FeedbackCopy.ratingLabel(star) }.boundsInRoot
                assertTrue(bounds.width >= 48 * DENSITY && bounds.height >= 48 * DENSITY, "star $star is ${bounds.size / DENSITY} dp")
            }
        }
    }

    /**
     * Both forms, empty, on their dialog's surface, 412 dp wide; [check] gets the frame, the surface's colour and every
     * node.
     */
    private fun forms(dark: Boolean, check: (Bitmap, Color, List<SemanticsNode>) -> Unit) {
        var surface = Color.Unspecified
        ImageComposeScene((412 * DENSITY).roundToInt(), (1700 * DENSITY).roundToInt(), Density(DENSITY)) {
            PortfolioTheme(darkTheme = dark) {
                surface = formDialogColor()
                Column(Modifier.fillMaxSize().background(surface)) {
                    MessageForm(MessageUiState.Idle, onSend = {}, onReset = {}, wide = false)
                    FeedbackForm(FeedbackUiState.Idle, onSend = {}, onReset = {}, wide = false)
                }
            }
        }.use { scene ->
            val image = scene.render()
            val nodes = scene.semanticsOwners.flatMap { it.unmergedRootSemanticsNode.withDescendants() }
            check(Bitmap.makeFromImage(image), surface, nodes)
        }
    }

    private val SemanticsNode.label: String?
        get() = config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    private fun SemanticsNode.withDescendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.withDescendants() }

    /** The pixel in [area] that stands out most from [background]: a border's or a stroke's own colour. */
    private fun strongest(image: Bitmap, background: Color, area: Rect): Color {
        var best = background
        for (x in max(0, area.left.toInt())..min(image.width - 1, area.right.toInt())) {
            for (y in max(0, area.top.toInt())..min(image.height - 1, area.bottom.toInt())) {
                val pixel = Color(image.getColor(x, y))
                if (contrast(pixel, background) > contrast(best, background)) best = pixel
            }
        }
        return best
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private companion object {
        const val DENSITY = 2f
    }
}
