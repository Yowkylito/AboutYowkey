package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.validJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticHtmlTest {

    private val portfolio = PortfolioParser.parse(validJson)

    @Test
    fun contentEscapesHtml() {
        val html = StaticHtml.content(portfolio)
        assertTrue("First &lt;project&gt;" in html)
        assertFalse("<project>" in html)
    }

    @Test
    fun pageFillsEveryPlaceholder() {
        val page = StaticHtml.page(portfolio, "<title>{{title}}</title><script>{{jsonLd}}</script><main>{{content}}</main>")
        assertFalse("{{" in page)
        assertTrue("\"@type\":\"Person\"" in page)
    }

    @Test
    fun pageRejectsUnknownPlaceholders() {
        assertFailsWith<IllegalArgumentException> { StaticHtml.page(portfolio, "{{nope}}") }
    }

    @Test
    fun factsAreStrongAndStillEscaped() {
        assertEquals(
            "<strong class=\"win\">53%</strong> of <strong>413</strong> &lt;commits&gt; in 2026",
            StaticHtml.emphasize("53% of 413 <commits> in 2026"),
        )
    }

    @Test
    fun jsonLdCannotCloseTheScriptTag() {
        val hostile = portfolio.copy(profile = portfolio.profile.copy(summary = "</script><b>"))
        val page = StaticHtml.page(hostile, "<script>{{jsonLd}}</script>")
        assertFalse("</script><b>" in page)
    }
}
