package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.ui.image.AnimatedClip
import dev.yowkey.portfolio.ui.image.ClipFrames
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs against the real content and template, so what ships is what is tested. */
class SiteContentTest {

    private val resources = File("src/commonMain/composeResources")
    private val portfolio = PortfolioParser.parse(File(resources, "files/portfolio.json").readText())
    private val page = StaticHtml.page(portfolio, File("web/index.template.html").readText())
    private val gallery = portfolio.projects.flatMap { it.gallery }
    private val stills = gallery.filterNot { it.isClip }
    private val clips = gallery.filter { it.isClip }

    @Test
    fun everyProjectAndJobIsInTheCrawlablePage() {
        portfolio.projects.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing project ${it.title}") }
        portfolio.experience.forEach { assertTrue(StaticHtml.escape(it.company) in page, "missing job at ${it.company}") }
        portfolio.services.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing service ${it.title}") }
        portfolio.recognition.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing award ${it.title}") }
    }

    @Test
    fun pageHasDiscoverabilityTags() {
        assertFalse("{{" in page)
        listOf("<title>", "name=\"description\"", "property=\"og:title\"", "rel=\"canonical\"", "application/ld+json")
            .forEach { assertTrue(it in page, "index.html lacks $it") }
    }

    /** Budgets keep the first visit fast: a card shows thumbnails only; full images load when opened. */
    @Test
    fun everyScreenshotAndCoverShipsWithinBudget() {
        stills.forEach { item ->
            val shot = item.shot
            val full = File(resources, shot.fullPath)
            val thumb = File(resources, shot.thumbPath)
            assertTrue(full.isFile, "missing ${full.path}")
            assertTrue(thumb.isFile, "missing ${thumb.path}")
            assertTrue(full.length() <= 260_000, "${full.name} is ${full.length() / 1024} KB, budget 254 KB")
            assertTrue(thumb.length() <= 64_000, "${thumb.name} is ${thumb.length() / 1024} KB, budget 62 KB")
            assertTrue(StaticHtml.resourceUrl(shot.thumbPath) in page, "${shot.file} is not in the static page")
        }
    }

    /** The viewer plays clips; the card and reduced motion show the poster; the static page animates the file. */
    @Test
    fun everyClipShipsWithItsPosterWithinBudget() {
        clips.forEach { item ->
            val shot = item.shot
            val clip = File(resources, shot.fullPath)
            val poster = File(resources, shot.posterPath)
            assertTrue(clip.isFile, "missing ${clip.path}")
            assertTrue(poster.isFile, "missing ${poster.path}")
            assertTrue(clip.length() <= 700L * 1024, "${clip.name} is ${clip.length() / 1024} KB, budget 700 KB")
            assertTrue(poster.length() <= 64_000, "${poster.name} is ${poster.length() / 1024} KB, budget 62 KB")
            assertTrue(StaticHtml.resourceUrl(shot.fullPath) in page, "${shot.file} is not in the static page")
            assertTrue(StaticHtml.resourceUrl(shot.posterPath) in page, "${shot.file}'s poster is not in the static page")
        }
    }

    /** Decodes each clip with the same Skia Codec the app plays it with, and holds it to the script's limits. */
    @Test
    fun everyClipDecodesWithinItsLimits() {
        clips.forEach { item ->
            val shot = item.shot
            val codec = Codec.makeFromData(Data.makeFromBytes(File(resources, shot.fullPath).readBytes()))
            assertTrue(codec.frameCount > 1, "${shot.file} is not animated")
            assertEquals(shot.width to shot.height, codec.width to codec.height, "${shot.file}: width/height in the JSON")
            assertTrue(maxOf(codec.width, codec.height) <= 720, "${shot.file} is over 720 px on its long edge")
            val millis = codec.framesInfo.sumOf { ClipFrames.delayMillis(it.duration) }
            assertTrue(millis <= 6_000, "${shot.file} runs ${millis} ms, at most 6 s")
            assertTrue(codec.frameCount * 1000 <= millis * 12 + 1000, "${shot.file} is over 12 fps")

            // And the player's path: every frame of a full loop, decoded in turn into the one bitmap.
            val player = AnimatedClip.decode(File(resources, shot.fullPath).readBytes())
            repeat(player.frameCount * 2) { player.show(ClipFrames.next(player.heldFrame, player.frameCount)) }
            assertEquals(0, player.heldFrame, "two loops end back on frame 0")
        }
    }

    @Test
    fun noScreenshotIsLeftUnlisted() {
        val listed = gallery.flatMap { item ->
            listOf(File(item.shot.fullPath).name, File(item.cardPath).name)
        }.toSet()
        val shipped = File(resources, "files/screens").listFiles().orEmpty().map { it.name }.toSet()
        assertTrue((shipped - listed).isEmpty(), "shipped but unused, remove them: ${shipped - listed}")
    }

    @Test
    fun repositoryVisibilityIsInTheCrawlablePage() {
        portfolio.projects.mapNotNull { it.visibility }.toSet().forEach {
            assertTrue(">${it.badge}</span>" in page, "missing badge ${it.badge}")
        }
        val anyPrivate = portfolio.projects.any { it.visibility == RepoVisibility.Private }
        assertEquals(anyPrivate, StaticHtml.escape(RepoVisibility.PRIVATE_NOTE) in page)
    }

    @Test
    fun coversComeFirstInTheirArticle() {
        portfolio.projects.forEach { project ->
            val cover = project.cover ?: return@forEach
            val article = page.substringAfter("<h3>${StaticHtml.escape(project.title)}</h3>", missingDelimiterValue = "")
            assertFalse(StaticHtml.resourceUrl(cover.thumbPath) in article, "${project.id}: the cover is not before its title")
            assertTrue(StaticHtml.resourceUrl(cover.thumbPath) in page, "${project.id}: cover missing from the page")
        }
    }
}
