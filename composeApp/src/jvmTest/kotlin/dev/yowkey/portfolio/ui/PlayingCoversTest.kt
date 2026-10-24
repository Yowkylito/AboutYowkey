package dev.yowkey.portfolio.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.samplePortfolio
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which covers play follows the corner Back to top keeps clear as it is now. The page hands in a new `keepClearPx`
 * whenever that corner moves (another gutter, a host button at the bottom end) or the page is covered, and the
 * playing set must use the newest, not the one it was first given.
 */
class PlayingCoversTest {

    private val clip = Project(
        id = "clip", title = "Clip", summary = "S",
        coverClip = Screenshot("clip.webp", "A clip", 332, 720),
    )
    private val items = buildPageItems(samplePortfolio(listOf(clip)), serviceColumns = 1, projectColumns = 1)

    @Test
    fun aCoverStopsWhenTheCornerItMustKeepClearGrowsOverIt() {
        val keepClear: MutableState<() -> Int> = mutableStateOf({ 0 })
        lateinit var playing: State<Set<String>>
        // An 800 px list whose rows are 300 px each: the project row (the second) ends at 600 px.
        ImageComposeScene(400, 800, Density(1f)) {
            val list = rememberLazyListState()
            playing = rememberPlayingCovers(list, items, active = true, keepClearPx = keepClear.value)
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                items(items) { Spacer(Modifier.fillMaxWidth().height(300.dp)) }
            }
        }.use { scene ->
            scene.render(0)
            scene.render(16_000_000)
            assertEquals(setOf("clip"), playing.value, "nothing to keep clear: the cover on screen plays")

            keepClear.value = { 300 } // the corner now reaches up to 500 px, over the row's end at 600 px
            scene.render(32_000_000)
            scene.render(48_000_000)
            assertEquals(emptySet(), playing.value, "the row reaches into the corner: its cover stops")
        }
    }
}
