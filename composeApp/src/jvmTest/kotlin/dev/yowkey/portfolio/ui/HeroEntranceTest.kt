package dev.yowkey.portfolio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.RecomposerInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.ui.image.LruLoader
import dev.yowkey.portfolio.ui.image.LocalScreenshotImages
import dev.yowkey.portfolio.ui.image.ScreenshotImages
import dev.yowkey.portfolio.ui.sections.Hero
import dev.yowkey.portfolio.ui.sections.HeroEntrance
import dev.yowkey.portfolio.ui.sections.HeroMotion
import dev.yowkey.portfolio.ui.sections.IntroStep
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The hero's entrance runs on one clock read in the draw and layer phases, so playing it never recomposes the hero.
 * It plays once: the words never wait long for the portrait, never hide again once shown, and under reduced motion
 * never hide at all.
 */
class HeroEntranceTest {

    private val end = HeroMotion.END_MS.toFloat()

    @Test
    fun theEntrancePlaysThroughWithoutRecomposingTheHero() {
        val hero = HeroScene(photo = true)
        hero.scene().use { scene ->
            val clock = Clock(scene)
            repeat(4) { clock.frame() }
            assertTrue(hero.entrance.portraitOnClock, "the portrait came in time and dashes on the clock")
            assertTrue(hero.entrance.clock.value in 1f..100f, "the entrance is under way: ${hero.entrance.clock.value}")
            assertEquals(0f, hero.entrance.shown(IntroStep.NAME), "the words wait for the landing")

            val recompositions = Recompositions(hero.recomposersBefore)
            var nameAtLanding = 0f
            repeat(90) {
                clock.frame()
                if (hero.entrance.clock.value < HeroMotion.TEXT_AT_MS + 120) nameAtLanding = hero.entrance.shown(IntroStep.NAME)
            }
            assertTrue(nameAtLanding > 0f && nameAtLanding < 1f, "the name was reading in: $nameAtLanding")
            assertEquals(end, hero.entrance.clock.value, "played through")
            (0 until HeroMotion.TEXT_STEPS).forEach { assertEquals(1f, hero.entrance.shown(it), "step $it shows") }
            assertEquals(0, recompositions.scopes, "the whole entrance was drawn, never composed")

            hero.probe.value++
            clock.frame()
            assertTrue(recompositions.scopes > 0, "the count does see a recomposition")
        }
    }

    @Test
    fun itPlaysOnceAndNeverHidesAWordAgain() {
        val hero = HeroScene(photo = true)
        hero.scene().use { scene ->
            val clock = Clock(scene)
            repeat(100) { clock.frame() }
            assertEquals(end, hero.entrance.clock.value)
            hero.present.value = false // the row scrolled away, or a project opened
            clock.frame()
            hero.present.value = true
            clock.frame()
            assertEquals(end, hero.entrance.clock.value, "not again")
            assertEquals(1f, hero.entrance.shown(IntroStep.NAME), "and the words stay")
        }
    }

    @Test
    fun underReducedMotionEverythingShowsAtOnce() {
        val hero = HeroScene(photo = true, reducedMotion = true)
        hero.scene().use { scene ->
            Clock(scene).frame()
            assertEquals(end, hero.entrance.clock.value)
            (0 until HeroMotion.TEXT_STEPS).forEach { assertEquals(1f, hero.entrance.shown(it), "step $it shows at once") }
        }
    }

    @Test
    fun withoutAPhotoTheWordsReadInAtOnce() {
        val hero = HeroScene(photo = false)
        hero.scene().use { scene ->
            val clock = Clock(scene)
            repeat(3) { clock.frame() }
            assertFalse(hero.entrance.portraitOnClock)
            assertTrue(hero.entrance.clock.value >= HeroMotion.TEXT_AT_MS, "no wait: ${hero.entrance.clock.value}")
            repeat(60) { clock.frame() }
            assertEquals(1f, hero.entrance.shown(IntroStep.SUMMARY))
        }
    }

    @Test
    fun aPortraitThatNeverLoadsHoldsTheWordsBackNoLongerThanTheWait() {
        val hero = HeroScene(photo = true, image = { awaitCancellation() })
        hero.scene().use { scene ->
            val clock = Clock(scene)
            repeat(3) { clock.frame() }
            assertEquals(0f, hero.entrance.shown(IntroStep.NAME), "waiting for the portrait")
            // The wait is a coroutine timeout, which the scene's virtual frames do not hurry along.
            Thread.sleep(HeroEntrance.PORTRAIT_WAIT_MS + 300)
            repeat(60) { clock.frame() }
            assertFalse(hero.entrance.portraitOnClock, "the words went ahead; the portrait fades in on its own")
            assertEquals(1f, hero.entrance.shown(IntroStep.SUMMARY))
        }
    }

    /** A 412 x 915 phone's hero (compact) with a portrait from [image], counting nothing until asked. */
    private class HeroScene(
        photo: Boolean,
        private val reducedMotion: Boolean = false,
        private val image: suspend (String) -> ImageBitmap = { ImageBitmap(64, 76) },
    ) {
        val entrance = HeroEntrance(if (reducedMotion) HeroMotion.END_MS.toFloat() else 0f)
        val probe = mutableStateOf(0)
        val present = mutableStateOf(true)
        val recomposersBefore = Recomposer.runningRecomposers.value
        private val profile = Profile(
            name = "Ada Lovelace",
            headline = "Engineer · Kotlin",
            summary = "Ships Compose apps end to end.",
            email = "ada@example.com",
            photo = if (photo) "portrait.webp" else null,
            proof = listOf(ProofPoint("53%", "of the code")),
        )
        private val images = ScreenshotImages(
            thumbnails = LruLoader(1L shl 20, Dispatchers.Unconfined) { image(it) },
            viewer = LruLoader(1L, Dispatchers.Unconfined) { error("unused") },
            covers = LruLoader(1L, Dispatchers.Unconfined) { error("unused") },
        )

        fun scene() = ImageComposeScene(412, 915, Density(1f)) {
            PortfolioTheme {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion, LocalScreenshotImages provides images) {
                    Column {
                        Text("${probe.value}")
                        if (present.value) {
                            Hero(profile, compact = true, wide = false, onSeeProjects = {}, onContact = {}, entrance = entrance)
                        }
                    }
                }
            }
        }
    }

    /** Counts every composable scope entered, by any composition of the recomposers started since [before]. */
    @OptIn(ExperimentalComposeRuntimeApi::class)
    private class Recompositions(before: Set<RecomposerInfo>) : CompositionObserver, CompositionRegistrationObserver {
        var scopes = 0
            private set

        init {
            (Recomposer.runningRecomposers.value - before).forEach { it.observe(this) }
        }

        override fun onCompositionRegistered(composition: ObservableComposition) {
            composition.setObserver(this)
        }

        override fun onCompositionUnregistered(composition: ObservableComposition) = Unit
        override fun onBeginComposition(composition: ObservableComposition) = Unit
        override fun onScopeEnter(scope: RecomposeScope) {
            scopes++
        }

        override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
        override fun onScopeExit(scope: RecomposeScope) = Unit
        override fun onEndComposition(composition: ObservableComposition) = Unit
        override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
        override fun onScopeDisposed(scope: RecomposeScope) = Unit
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
