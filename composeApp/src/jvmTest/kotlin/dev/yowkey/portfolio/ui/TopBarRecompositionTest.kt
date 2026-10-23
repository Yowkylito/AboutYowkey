package dev.yowkey.portfolio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import dev.yowkey.portfolio.model.LiveStats
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.RatingSummary
import dev.yowkey.portfolio.model.VisitCount
import dev.yowkey.portfolio.ui.components.ProofReveal
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bar sits over every scroll frame, so it must not recompose on them: the brand slot (the only part that reads
 * `heroPassed`) recomposes once when the hero passes, and the reading line is only ever drawn. The proof in the slot
 * counts up in the draw phase too: only new numbers recompose it, and nothing plays its entrance twice.
 */
class TopBarRecompositionTest {

    private val proof = LiveStats(VisitCount(1234), RatingSummary(14, 4.8))

    @Test
    fun scrollingRecomposesTheBrandOnlyWhenTheHeroPasses() {
        val bar = Bar(proof)
        bar.scene().use { scene ->
            val clock = Clock(scene)
            clock.frame()
            assertEquals(1, bar.brandReads, "composed once")
            val drawnAtTop = bar.progressReads
            repeat(200) {
                bar.list.dispatchRawDelta(10f)
                clock.frame()
            }
            assertTrue(bar.list.firstVisibleItemIndex > 10, "the page did scroll")
            assertEquals(2, bar.brandReads, "200 scroll frames, one recomposition: the hero passing")
            assertTrue(bar.progressReads - drawnAtTop >= 150, "the line follows the scroll in the draw phase")
        }
    }

    @Test
    fun theProofCountsUpOnceWithoutRecomposing() {
        val bar = Bar(proof)
        bar.scene().use { scene ->
            val clock = Clock(scene)
            clock.frame()
            repeat(5) { clock.frame() }
            val count = bar.reveal.count.value
            assertTrue(count > 0f && count < 1f, "counting up: $count")
            repeat(70) { clock.frame() }
            assertEquals(1f, bar.reveal.count.value, "counted up within ${ProofReveal.COUNT_MS} ms")
            assertEquals(1f, bar.reveal.shown.value, "and faded in")
            assertEquals(1, bar.brandReads, "the whole count was drawn, never composed")

            bar.stats.value = LiveStats(VisitCount(1235), RatingSummary(15, 4.8))
            clock.frame()
            assertEquals(2, bar.brandReads, "a new answer recomposes the slot once")
            assertEquals(1f, bar.reveal.count.value, "and only changes the numbers")

            repeat(40) {
                bar.list.dispatchRawDelta(10f)
                clock.frame()
            }
            repeat(40) {
                bar.list.dispatchRawDelta(-10f)
                clock.frame()
            }
            assertEquals(0, bar.list.firstVisibleItemIndex)
            assertEquals(4, bar.brandReads, "the name came and went")
            assertEquals(1f, bar.reveal.count.value, "back at the hero, the count does not play again")
        }
    }

    @Test
    fun withNothingToShowTheProofWaitsAndNeverCounts() {
        val bar = Bar(LiveStats(VisitCount(0), RatingSummary()))
        bar.scene().use { scene ->
            val clock = Clock(scene)
            repeat(60) { clock.frame() }
            assertEquals(0f, bar.reveal.shown.value, "an empty slot")
            assertEquals(0f, bar.reveal.count.value)

            bar.stats.value = proof
            clock.frame()
            repeat(5) { clock.frame() }
            assertTrue(bar.reveal.shown.value > 0f, "it fades in as the numbers arrive")
            val count = bar.reveal.count.value
            assertTrue(count > 0f && count < 1f, "and counts up from 0: $count")
        }
    }

    @Test
    fun withOneAnswerInTheProofWaitsForTheOther() {
        val bar = Bar(LiveStats(visits = VisitCount(35)))
        bar.scene().use { scene ->
            val clock = Clock(scene)
            repeat(10) { clock.frame() }
            assertEquals(0f, bar.reveal.shown.value, "the visits alone: a moment for the rating")

            bar.stats.value = LiveStats(VisitCount(35), RatingSummary(1, 5.0))
            clock.frame()
            repeat(5) { clock.frame() }
            assertTrue(bar.reveal.shown.value > 0f, "the rating came: no more waiting")
        }
        val alone = Bar(LiveStats(visits = VisitCount(35)))
        alone.scene().use { scene ->
            val clock = Clock(scene)
            clock.frame()
            // The wait is a coroutine delay, which the scene's virtual frames do not hurry along.
            Thread.sleep(ProofReveal.SETTLE_WAIT_MS + 400)
            repeat(10) { clock.frame() }
            assertTrue(alone.reveal.shown.value > 0f, "it never came: the visits show on their own")
        }
    }

    /** A 1280 x 800 bar over a list whose 600 px hero passes at 300 px, counting what it reads. */
    private class Bar(initial: LiveStats) {
        val stats: MutableState<LiveStats> = mutableStateOf(initial)
        val reveal = ProofReveal()
        var brandReads = 0
        var progressReads = 0
        lateinit var list: LazyListState

        fun scene() = ImageComposeScene(1280, 800, Density(1f)) {
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
                        stats = stats.value,
                        onOpenNotes = {},
                        onNavigate = {},
                        proofReveal = reveal,
                    )
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        item { Spacer(Modifier.fillMaxWidth().height(600.dp)) }
                        items(40) { Spacer(Modifier.fillMaxWidth().height(100.dp)) }
                    }
                }
            }
        }
    }

    /** 60 frames a second. */
    private class Clock(private val scene: ImageComposeScene) {
        private var time = 0L

        fun frame() {
            time += 16_000_000L
            scene.render(time)
        }
    }
}
