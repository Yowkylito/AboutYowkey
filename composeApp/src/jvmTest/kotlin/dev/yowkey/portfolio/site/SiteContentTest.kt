package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs against the real content and template, so what ships is what is tested. */
class SiteContentTest {

    private val portfolio = PortfolioParser.parse(File("src/commonMain/composeResources/files/portfolio.json").readText())
    private val page = StaticHtml.page(portfolio, File("web/index.template.html").readText())

    @Test
    fun everyProjectAndJobIsInTheCrawlablePage() {
        portfolio.projects.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing project ${it.title}") }
        portfolio.experience.forEach { assertTrue(StaticHtml.escape(it.company) in page, "missing job at ${it.company}") }
        portfolio.services.forEach { assertTrue(StaticHtml.escape(it.title) in page, "missing service ${it.title}") }
    }

    @Test
    fun pageHasDiscoverabilityTags() {
        assertFalse("{{" in page)
        listOf("<title>", "name=\"description\"", "property=\"og:title\"", "rel=\"canonical\"", "application/ld+json")
            .forEach { assertTrue(it in page, "index.html lacks $it") }
    }
}
