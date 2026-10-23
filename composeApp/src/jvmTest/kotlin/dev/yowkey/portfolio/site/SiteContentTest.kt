package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs against the real content and template, so what ships is what is tested. */
class SiteContentTest {

    private val resources = File("src/commonMain/composeResources")
    private val portfolio = PortfolioParser.parse(File(resources, "files/portfolio.json").readText())
    private val page = StaticHtml.page(portfolio, File("web/index.template.html").readText())
    private val screenshots = portfolio.projects.flatMap { it.screenshots }

    @Test
    fun everyProjectAndJobIsInTheCrawlablePage() {
        portfolio.projects.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing project ${it.title}") }
        portfolio.experience.forEach { assertTrue(StaticHtml.escape(it.company) in page, "missing job at ${it.company}") }
        portfolio.services.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing service ${it.title}") }
        portfolio.recognition.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing award ${it.title}") }
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
    fun everyScreenshotShipsWithinBudget() {
        screenshots.forEach { shot ->
            val full = File(resources, shot.fullPath)
            val thumb = File(resources, shot.thumbPath)
            assertTrue(full.isFile, "missing ${full.path}")
            assertTrue(thumb.isFile, "missing ${thumb.path}")
            assertTrue(full.length() <= 260_000, "${full.name} is ${full.length() / 1024} KB, budget 254 KB")
            assertTrue(thumb.length() <= 64_000, "${thumb.name} is ${thumb.length() / 1024} KB, budget 62 KB")
            assertTrue(StaticHtml.resourceUrl(shot.thumbPath) in page, "${shot.file} is not in the static page")
        }
    }

    @Test
    fun noScreenshotIsLeftUnlisted() {
        val listed = screenshots.flatMap { listOf(File(it.fullPath).name, File(it.thumbPath).name) }.toSet()
        val shipped = File(resources, "files/screens").listFiles().orEmpty().map { it.name }.toSet()
        assertTrue((shipped - listed).isEmpty(), "shipped but unused, remove them: ${shipped - listed}")
    }
}
