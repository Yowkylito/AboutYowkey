package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.ui.image.AnimatedClip
import dev.yowkey.portfolio.ui.image.ClipFrames
import dev.yowkey.portfolio.ui.image.ScreenshotImages
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.io.File
import kotlin.math.abs
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

    /** The detail screen's engineering content is in the crawlable page too, every word of it. */
    @Test
    fun everyProjectsEngineeringIsInTheCrawlablePage() {
        portfolio.projects.forEach { project ->
            val article = page.substringAfter("<article id=\"${project.anchor}\">").substringBefore("</article>")
            project.tagline?.let { assertTrue(StaticHtml.escape(it) in article, "${project.id}: tagline") }
            val e = project.engineering ?: return@forEach
            (e.builtWith + e.platforms + e.quality + listOfNotNull(e.architecture) + e.decisions.flatMap { listOf(it.title, it.detail) } +
                e.modules.flatMap { listOf(it.name, it.role) + it.dependsOn })
                .forEach { assertTrue(StaticHtml.escape(it) in article, "${project.id}: missing \"$it\"") }
        }
    }

    /** The seven-second skim: every point is in the hero, ahead of the first section. */
    @Test
    fun heroShowsProofAvailabilityAndContact() {
        val hero = page.substringBefore("</header>")
        val proof = portfolio.profile.proof
        assertTrue(proof.size >= 2, "the hero needs at least two proof points")
        proof.forEach { assertTrue(StaticHtml.escape(it.label) in hero, "hero lacks proof ${it.value}") }
        listOfNotNull(portfolio.profile.location, portfolio.profile.availability, portfolio.profile.email)
            .forEach { assertTrue(StaticHtml.escape(it) in hero, "hero lacks $it") }
    }

    /** The CV carries a personal phone number; the site must never. */
    @Test
    fun noPhoneNumberIsPublished() {
        val phone = Regex("""(\+?63|\b0)[\s-]?9\d{2}[\s-]?\d{3}[\s-]?\d{4}""")
        val json = File(resources, "files/portfolio.json").readText()
        listOf(json, page).forEach { assertFalse(phone.containsMatchIn(it), "a phone number: ${phone.find(it)?.value}") }
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

    /**
     * The web viewer's 60 fps twins, held to `prepare_screenshots.py --video`: H.264 (every browser decodes it), the
     * clip's shape (the viewer sizes the box from the JSON), silent, index first so it plays while it downloads.
     */
    @Test
    fun everyClipVideoIsA60FpsH264WithinBudget() {
        clips.filter { it.shot.video }.forEach { item ->
            val shot = item.shot
            val file = File(resources, shot.videoPath)
            assertTrue(file.isFile, "missing ${file.path}")
            assertTrue(file.length() <= 1536L * 1024, "${file.name} is ${file.length() / 1024} KB, budget 1536 KB")
            val video = Mp4Info.read(file.readBytes())
            assertEquals("avc1", video.codec, "${file.name} is not H.264")
            assertEquals(60.0, video.framesPerSecond, 0.5, "${file.name}'s frame rate")
            assertTrue(video.seconds <= 6.05, "${file.name} runs ${video.seconds} s, at most 6 s")
            assertTrue(maxOf(video.width, video.height) <= 1440, "${file.name} is over 1440 px on its long edge")
            val ratio = video.width.toFloat() / video.height
            assertTrue(abs(ratio / shot.aspectRatio - 1) < 0.02, "${file.name} is not the shape of ${shot.file}")
            assertFalse(video.hasAudio, "${file.name} has a sound track")
            assertTrue(video.indexFirst, "${file.name} has its index after its frames, so it cannot play until fully loaded")
        }
    }

    @Test
    fun noScreenshotIsLeftUnlisted() {
        val listed = gallery.flatMap { item ->
            listOfNotNull(
                File(item.shot.fullPath).name,
                File(item.cardPath).name,
                File(item.shot.videoPath).name.takeIf { item.shot.video },
            )
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

    /** The card's lead (the cover clip, else the cover) opens its article, ahead of the title. */
    @Test
    fun coversComeFirstInTheirArticle() {
        portfolio.projects.forEach { project ->
            val lead = project.coverClip?.fullPath ?: project.cover?.thumbPath ?: return@forEach
            val article = page.substringAfter("<article id=\"${project.anchor}\">", missingDelimiterValue = "")
            assertTrue(article.isNotEmpty(), "${project.id}: no article with its id")
            val title = article.indexOf("<h3>${StaticHtml.escape(project.title)}</h3>")
            assertTrue(article.indexOf(StaticHtml.resourceUrl(lead)) in 0 until title, "${project.id}: the cover is not before its title")
        }
    }

    @Test
    fun everyProjectHasAnArticleTheDeepLinkCanReach() {
        portfolio.projects.forEach { assertTrue("<article id=\"project-${it.id}\">" in page, "no article for ${it.id}") }
        assertTrue("getElementById(\"project-\" + match[1])" in page, "the static page scrolls to a deep-linked project")
    }

    /**
     * Every project's animated splash cover: it leads the card and the static article, keeps the still cover and its
     * poster for reduced motion, and decodes and loops in the card's one reusable bitmap.
     */
    @Test
    fun everyCoverClipLeadsItsProjectAndPlays() {
        val withClip = portfolio.projects.filter { it.coverClip != null }
        assertTrue(withClip.isNotEmpty(), "no project has an animated cover")
        val reparsed = PortfolioParser.parse(Json.encodeToString(Portfolio.serializer(), portfolio))
        val html = StaticHtml.content(reparsed)
        withClip.forEach { project ->
            val clip = project.coverClip!!
            assertEquals(GalleryItem.Kind.CoverClip, project.leadMedia?.kind, project.id)
            if (project.cover != null) {
                assertEquals(listOf(GalleryItem.Kind.Cover, GalleryItem.Kind.CoverClip), project.gallery.take(2).map { it.kind }, project.id)
            }
            val article = html.substringAfter("<article id=\"project-${project.id}\">").substringBefore("</article>")
            assertTrue(article.indexOf(StaticHtml.resourceUrl(clip.fullPath)) < article.indexOf("<h3>"), "${project.id}: the clip leads the article")
            assertTrue(StaticHtml.resourceUrl(clip.posterPath) in article, "${project.id}: its poster for reduced motion")

            val player = AnimatedClip.decode(File(resources, clip.fullPath).readBytes())
            repeat(player.frameCount) { player.show(ClipFrames.next(player.heldFrame, player.frameCount)) }
            assertEquals(0, player.heldFrame, "${project.id}: one loop ends back on frame 0")
            assertTrue(File(resources, clip.posterPath).isFile, "${project.id}: poster missing")
        }
    }

    /** The cover clip budget holds the clips that ship today, decoded the way the cards hold them. */
    @Test
    fun coverClipBudgetFitsFourOfTodaysLargestClips() {
        val largest = clips.map { AnimatedClip.decode(File(resources, it.shot.fullPath).readBytes()).byteCount }.sortedDescending()
        assertTrue(largest.isNotEmpty())
        assertTrue(largest.take(4).sum() <= ScreenshotImages.COVER_CLIP_BUDGET, "four clips weigh ${largest.take(4).sum()} bytes")
    }
}
