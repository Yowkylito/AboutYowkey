package dev.yowkey.portfolio.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.ui.navigation.Route
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

/**
 * Back to top floats over the page's bottom end, and at the very end of the page nothing scrolls out from under it, so
 * the footer's words must keep out of its way on a phone, where they run the width of the screen.
 */
class PageEndTest {

    private val portfolio = PortfolioParser.parse(File("src/commonMain/composeResources/files/portfolio.json").readText())

    @Test
    fun theFooterIsNeverUnderBackToTop() {
        for (width in listOf(360, 412)) {
            ImageComposeScene(width, 780, Density(1f)) {
                PortfolioTheme {
                    PortfolioScreen(
                        state = PortfolioUiState.Ready(portfolio),
                        route = Route.Home,
                        platformLabel = "Kotlin/Wasm in your browser",
                        onRetry = {},
                        onOpenProject = {},
                        onBack = {},
                        onUnknownProject = {},
                        onContentShown = {},
                    )
                }
            }.use { scene ->
                var time = 0L
                fun frames(count: Int) = repeat(count) { scene.render(16_000_000L * ++time) }
                frames(10)
                // Down to the very end, as a wheel takes a reader there.
                repeat(120) {
                    scene.sendPointerEvent(PointerEventType.Scroll, Offset(width / 2f, 390f), scrollDelta = Offset(0f, 40f))
                    frames(4)
                }
                frames(40)
                val nodes = scene.semanticsOwners.flatMap { it.unmergedRootSemanticsNode.withDescendants() }
                val button = assertNotNull(nodes.firstOrNull { it.label == "Back to top" }, "Back to top shows at the end")
                val footer = assertNotNull(nodes.firstOrNull { it.text?.startsWith("Built with Compose Multiplatform") == true })
                assertFalse(
                    footer.boundsInRoot.overlaps(button.boundsInRoot),
                    "at $width dp the footer (${footer.boundsInRoot}) runs under Back to top (${button.boundsInRoot})",
                )
            }
        }
    }

    private val SemanticsNode.label: String?
        get() = config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    private val SemanticsNode.text: String?
        get() = config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

    private fun SemanticsNode.withDescendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.withDescendants() }
}
