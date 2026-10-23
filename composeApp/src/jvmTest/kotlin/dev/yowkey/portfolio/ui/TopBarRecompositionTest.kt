package dev.yowkey.portfolio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bar sits over every scroll frame, so it must not recompose on them: the brand slot (the only part that reads
 * `heroPassed`) recomposes once when the hero passes, and the reading line is only ever drawn.
 */
class TopBarRecompositionTest {

    @Test
    fun scrollingRecomposesTheBrandOnlyWhenTheHeroPasses() {
        var brandReads = 0
        var progressReads = 0
        lateinit var list: LazyListState
        ImageComposeScene(1280, 800, Density(1f)) {
            PortfolioTheme {
                val state = rememberLazyListState().also { list = it }
                val passed = rememberHeroPassed(state)
                val heroPassed = remember(passed) {
                    {
                        brandReads++
                        passed()
                    }
                }
                val progress = remember(state) {
                    {
                        progressReads++
                        state.layoutInfo.readingProgress()
                    }
                }
                Column {
                    TopBar(
                        profile = Profile("Ada Lovelace", "Engineer · Kotlin", "Summary.", availability = "Open to work"),
                        sections = Section.entries.drop(1),
                        active = Section.Home,
                        compact = false,
                        gutter = 48.dp,
                        maxWidth = 1120.dp,
                        scrolled = { state.canScrollBackward },
                        heroPassed = heroPassed,
                        progress = progress,
                        onNavigate = {},
                    )
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        // A 600 px hero, so it passes at 300 px, then plenty of page.
                        item { Spacer(Modifier.fillMaxWidth().height(600.dp)) }
                        items(40) { Spacer(Modifier.fillMaxWidth().height(100.dp)) }
                    }
                }
            }
        }.use { scene ->
            var time = 0L
            fun frame() {
                time += 16_000_000L
                scene.render(time)
            }
            frame()
            assertEquals(1, brandReads, "composed once")
            val drawnAtTop = progressReads
            repeat(200) {
                list.dispatchRawDelta(10f)
                frame()
            }
            assertTrue(list.firstVisibleItemIndex > 10, "the page did scroll")
            assertEquals(2, brandReads, "200 scroll frames, one recomposition: the hero passing")
            assertTrue(progressReads - drawnAtTop >= 150, "the line follows the scroll in the draw phase")
        }
    }
}
