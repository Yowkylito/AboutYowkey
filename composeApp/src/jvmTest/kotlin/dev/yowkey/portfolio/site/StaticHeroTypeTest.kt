package dev.yowkey.portfolio.site

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.window.core.layout.WindowSizeClass
import dev.yowkey.portfolio.ui.sections.readingMeasure
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The static page's hero is set in the app's type, so the app, fading in over it, lands on the same lines: each
 * text's size, line height, letter spacing and weight in the template are the app's for that text (Hero.kt), the
 * name switches to the larger size where the app's layout stops being compact, and the headline and summary run to
 * the app's reading measure. Read from the template itself, so a change to either side fails here.
 */
class StaticHeroTypeTest {

    private val styles = File("web/index.template.html").readText().substringAfter("<style>").substringBefore("</style>")

    @Test
    fun theStaticHeroIsSetInTheAppsType() {
        val type = appTypography()
        val medium = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        assertSetAs(type.displayMedium, rule("    .hero h1 {"), "the name on a phone")
        assertSetAs(type.displayLarge, rule("@media (min-width: ${medium}px) { .hero h1 {"), "the name from ${medium}px", inheritsWeight = true)
        assertSetAs(type.headlineSmall, rule("    .hero .headline {"), "the headline")
        assertSetAs(type.headlineMedium, rule("    .hero .proof strong {"), "a proof point's figure")
        assertSetAs(type.bodyMedium, rule("    .hero .proof li {"), "a proof point's sentence")
        assertSetAs(type.labelLarge, rule("    .hero .links a {"), "a button")
        assertSetAs(type.bodyLarge, rule("    .hero > .links + p {"), "the summary")

        val density = Density(1f)
        mapOf("    .hero .headline {" to type.headlineSmall, "    .hero > .links + p {" to type.bodyLarge).forEach { (selector, style) ->
            assertEquals(readingMeasure(style.fontSize, density).value, px(rule(selector), "max-width"), "$selector reading measure")
        }
    }

    /** [rule] sets [style]'s size, line height, letter spacing and (unless it keeps a wider rule's) weight. */
    private fun assertSetAs(style: TextStyle, rule: String, what: String, inheritsWeight: Boolean = false) {
        assertEquals(style.fontSize.value, px(rule, "font-size"), "$what: size")
        assertEquals(style.lineHeight.value, px(rule, "line-height"), "$what: line height")
        assertEquals(style.letterSpacing.value.takeUnless { it.isNaN() } ?: 0f, px(rule, "letter-spacing") ?: 0f, "$what: letter spacing")
        if (inheritsWeight) return
        // Unset, the hero's paragraphs and list items keep the page's regular weight.
        val weight = Regex("font-weight:\\s*(\\d+)").find(rule)?.groupValues?.get(1)?.toInt() ?: FontWeight.Normal.weight
        assertEquals((style.fontWeight ?: FontWeight.Normal).weight, weight, "$what: weight")
    }

    /** The declarations of the rule that starts with [start] (a selector, or a media query and its selector). */
    private fun rule(start: String): String {
        val at = styles.indexOf(start)
        check(at >= 0) { "no rule $start" }
        return styles.substring(at + start.length).substringBefore("}")
    }

    private fun px(rule: String, property: String): Float? =
        Regex("(?<![-\\w])$property:\\s*(-?[\\d.]+)px").find(rule)?.groupValues?.get(1)?.toFloat()

    private fun appTypography(): Typography {
        var type: Typography? = null
        ImageComposeScene(width = 10, height = 10) { PortfolioTheme { type = MaterialTheme.typography } }.use { it.render() }
        return assertNotNull(type)
    }
}
