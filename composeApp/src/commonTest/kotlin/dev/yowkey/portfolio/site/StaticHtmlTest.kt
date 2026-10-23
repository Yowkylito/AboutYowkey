package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.model.Decision
import dev.yowkey.portfolio.model.Engineering
import dev.yowkey.portfolio.model.Module
import dev.yowkey.portfolio.model.ProofPoint
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
        val article = html.substringAfter("<article id=\"project-two\">")
        assertTrue(article.indexOf("<img") == article.indexOf("<img class=\"cover portrait\""), "the cover comes first")
        assertTrue(article.indexOf("splash.thumb.webp") < article.indexOf("ride.webp"))
        assertTrue(article.indexOf("ride.webp") < article.indexOf("shot.thumb.webp"), "clips before screenshots")
        assertTrue(
            "<source media=\"(prefers-reduced-motion: reduce)\" srcset=\"${StaticHtml.resourceUrl(media.posterPath)}\">" +
                "<img src=\"${StaticHtml.resourceUrl(media.fullPath)}\" alt=\"A &quot;ride&quot; &lt;clip&gt;\" " +
                "width=\"480\" height=\"270\" loading=\"lazy\"" in html,
        )
    }

    private val engineering = Engineering(
        builtWith = listOf("Kotlin Multiplatform", "Compose <Multiplatform>"),
        platforms = listOf("Android", "iOS"),
        architecture = "Layers & \"modules\".",
        decisions = listOf(Decision("Hash router", "Two routes <need> no library."), Decision("Static first", "Crawlers.")),
        modules = listOf(Module(":app", "Entry", listOf(":core")), Module(":core", "Types <pure>")),
        quality = listOf("800 unit tests", "CI on <GitHub> Actions"),
    )

    @Test
    fun workLeadsTheProjectsWithItsFiguresAndTitledParts() {
        val work = portfolio.projects[1].copy(
            company = "Acme <Co>",
            role = "Android developer",
            figures = listOf(ProofPoint("53%", "of the <code>")),
            impact = listOf("IMPACT"),
            highlights = listOf("CONTRIBUTION"),
            optimizations = listOf("AUDIT"),
        )
        val html = StaticHtml.content(portfolio.copy(projects = listOf(portfolio.projects[0], work)))
        val section = html.substringAfter("<section id=\"projects\">")
        assertTrue(section.indexOf("<article id=\"project-two\">") in 0 until section.indexOf("<article id=\"project-one\">"), "work comes first")
        assertTrue(
            section.indexOf("<h3 class=\"group\">Professional work</h3>") in 0 until section.indexOf("<h3 class=\"group\">Personal projects</h3>"),
        )
        assertTrue("Products I build at Acme &lt;Co&gt;." in html, "the work note names the company, escaped")
        val article = html.substringAfter("<article id=\"project-two\">").substringBefore("</article>")
        listOf(
            "<h4>Second</h4>",
            "<p class=\"muted\">Acme &lt;Co&gt; · Android developer</p>",
            "<li><strong>53%</strong> <span>of the &lt;code&gt;</span></li>",
        ).forEach { assertTrue(it in article, "missing $it") }
        val order = listOf(
            "<ul class=\"figures\">", "<h5>Impact</h5>", "IMPACT", "<h5>My contributions</h5>", "CONTRIBUTION",
            "<h5>Optimizations and audits</h5>", "AUDIT",
        )
        val positions = order.map { article.indexOf(it) }
        assertTrue(positions.none { it < 0 } && positions == positions.sorted(), "order ${order.zip(positions)}")
    }

    @Test
    fun everyArticleCarriesItsProjectIdForDeepLinks() {
        val html = StaticHtml.content(portfolio)
        assertTrue("<article id=\"project-one\">" in html)
        assertTrue("<article id=\"project-two\">" in html)
    }

    @Test
    fun engineeringIsCrawlableAndEscaped() {
        val project = portfolio.projects[1].copy(tagline = "One <line>", engineering = engineering)
        val html = StaticHtml.content(portfolio.copy(projects = listOf(project)))
        listOf(
            "<p class=\"tagline\">One &lt;line&gt;</p>",
            "<dt>Built with</dt><dd>Kotlin Multiplatform · Compose &lt;Multiplatform&gt;</dd>",
            "<dt>Runs on</dt><dd>Android · iOS</dd>",
            "<h5>How it&#39;s built</h5>",
            "<p>Layers &amp; &quot;modules&quot;.</p>",
            // Modules as a nested list: each module, then what it depends on.
            "<li><code>:app</code> Entry<ul><li>depends on <code>:core</code></li></ul></li>",
            "<li><code>:core</code> Types &lt;pure&gt;</li>",
            "<dt>Hash router</dt><dd>Two routes &lt;need&gt; no library.</dd>",
            "<ul class=\"quality\"><li>800 unit tests</li><li>CI on &lt;GitHub&gt; Actions</li></ul>",
        ).forEach { assertTrue(it in html, "missing $it") }
        assertFalse("<need>" in html || "<pure>" in html)
    }

    @Test
    fun theCardsAnswersComeFirstThenTheDetail() {
        val project = portfolio.projects[1].copy(
            tagline = "TAGLINE",
            highlights = listOf("HIGHLIGHT"),
            stack = listOf("STACK"),
            screenshots = listOf(Screenshot("shot.webp", "Shot", 800, 600)),
            engineering = engineering,
            cover = Screenshot("splash.webp", "Splash", 720, 1560),
        )
        val article = StaticHtml.content(portfolio.copy(projects = listOf(project))).substringAfter("<article id=\"project-two\">")
        val order = listOf("splash.thumb.webp", "<h4>", "TAGLINE", "<p>S</p>", "Built with", "HIGHLIGHT", "shot.thumb.webp", "How it&#39;s built", "STACK")
        val positions = order.map { article.indexOf(it) }
        assertTrue(positions.none { it < 0 } && positions == positions.sorted(), "order ${order.zip(positions)}")
    }

    @Test
    fun aCoverClipLeadsWithItsPosterUnderReducedMotionAndTheStillCoverJoinsTheGallery() {
        val project = portfolio.projects[1].copy(
            cover = Screenshot("splash.webp", "Splash", 720, 1560),
            coverClip = Screenshot("intro.webp", "Intro", 324, 720),
            clips = listOf(media),
        )
        val article = StaticHtml.content(portfolio.copy(projects = listOf(project))).substringAfter("<article id=\"project-two\">")
        val intro = Screenshot("intro.webp", "Intro", 324, 720)
        val lead = "<picture><source media=\"(prefers-reduced-motion: reduce)\" srcset=\"${StaticHtml.resourceUrl(intro.posterPath)}\">" +
            "<img class=\"cover portrait\" src=\"${StaticHtml.resourceUrl(intro.fullPath)}\""
        assertTrue(article.indexOf(lead) in 0 until article.indexOf("<img"), "the cover clip is the first image: ${article.take(300)}")
        assertTrue(article.indexOf("intro.webp") < article.indexOf("<h4>"), "the cover clip leads")
        val shots = article.substringAfter("<div class=\"shots\">")
        assertTrue(shots.indexOf("splash.thumb.webp") in 0 until shots.indexOf("ride.webp"), "the still cover opens the gallery")
    }
}
