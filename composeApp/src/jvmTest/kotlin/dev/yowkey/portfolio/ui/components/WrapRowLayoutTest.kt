package dev.yowkey.portfolio.ui.components

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The card grid levels its rows with `IntrinsicSize.Min`, so the chip group must report, before layout, the height
 * it then draws. FlowRow under-reported it, which squeezed the card's "View project" button on phones.
 */
class WrapRowLayoutTest {

    private fun measure(intrinsic: Boolean): IntSize {
        var size = IntSize.Zero
        ImageComposeScene(412, 400, Density(1f)) {
            PortfolioTheme {
                val chips = @androidx.compose.runtime.Composable {
                    WrapRow(Modifier.width(324.dp).onSizeChanged { size = it }) {
                        listOf("Kotlin", "Jetpack Compose", "Room", "SQLCipher", "Koin", "AndroidX Biometric").forEach { TechChip(it) }
                        listOf("Android", "iOS").forEach { PlatformChip(it) }
                    }
                }
                if (intrinsic) Row(Modifier.height(IntrinsicSize.Min)) { chips() } else chips()
            }
        }.use { it.render() }
        return size
    }

    @Test
    fun intrinsicHeightMatchesTheDrawnHeight() {
        val natural = measure(intrinsic = false)
        assertTrue(natural.height > 60, "several lines of chips at phone width: $natural")
        assertEquals(natural, measure(intrinsic = true))
    }
}
