package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.validJson
import kotlin.test.Test
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
    fun jsonLdCannotCloseTheScriptTag() {
        val hostile = portfolio.copy(profile = portfolio.profile.copy(summary = "</script><b>"))
        val page = StaticHtml.page(hostile, "<script>{{jsonLd}}</script>")
        assertFalse("</script><b>" in page)
    }

    private val media = Screenshot("ride.webp", "A \"ride\" <clip>", 480, 270)

    @Test
    fun visibilityBadgesAndThePrivateNoteAreEscapedAndConditional() {
        val none = StaticHtml.content(portfolio)
        assertFalse("class=\"badge\"" in none)
        assertFalse("class=\"note\"" in none)

        val publicOnly = portfolio.copy(projects = portfolio.projects.map { it.copy(visibility = RepoVisibility.Public) })
        val publicHtml = StaticHtml.content(publicOnly)
        assertTrue("<span class=\"badge\">Public repo</span>" in publicHtml)
        assertFalse("class=\"note\"" in publicHtml, "the note is only for private projects")

        val mixed = portfolio.copy(projects = listOf(portfolio.projects[0].copy(visibility = RepoVisibility.Private), portfolio.projects[1]))
        val html = StaticHtml.content(mixed)
        assertTrue("<span class=\"badge\">Private repo</span>" in html)
        assertTrue("<p class=\"note\">${StaticHtml.escape(RepoVisibility.PRIVATE_NOTE)}</p>" in html)
        assertTrue("isn&#39;t on my profile" in html, "the note's apostrophe is escaped")
    }

    @Test
    fun coverIsTheArticlesFirstImageAndClipsAnimateWithAPosterForReducedMotion() {
        val shot = Screenshot("shot.webp", "Shot", 800, 600)
        val project = portfolio.projects[1].copy(cover = Screenshot("splash.webp", "Splash", 720, 1560), clips = listOf(media), screenshots = listOf(shot))
        val html = StaticHtml.content(portfolio.copy(projects = listOf(project)))
        val article = html.substringAfter("<article>")
        assertTrue(article.indexOf("<img") == article.indexOf("<img class=\"cover portrait\""), "the cover comes first")
        assertTrue(article.indexOf("splash.thumb.webp") < article.indexOf("ride.webp"))
        assertTrue(article.indexOf("ride.webp") < article.indexOf("shot.thumb.webp"), "clips before screenshots")
        assertTrue(
            "<source media=\"(prefers-reduced-motion: reduce)\" srcset=\"${StaticHtml.resourceUrl(media.posterPath)}\">" +
                "<img src=\"${StaticHtml.resourceUrl(media.fullPath)}\" alt=\"A &quot;ride&quot; &lt;clip&gt;\" " +
                "width=\"480\" height=\"270\" loading=\"lazy\"" in html,
        )
    }
}
