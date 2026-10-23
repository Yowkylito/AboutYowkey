package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.data.PortfolioParser
import dev.yowkey.portfolio.model.Decision
import dev.yowkey.portfolio.model.Engineering
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.MessageCopy
import dev.yowkey.portfolio.model.Module
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.model.Relation
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.model.Screenshot
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
        val template = "<title>{{title}}</title><script>{{jsonLd}}</script><main>{{content}}</main>" +
            "<script>script.src = \"composeApp.js?v={{build}}\";</script>"
        val page = StaticHtml.page(portfolio, template, "949ee26")
        assertFalse("{{" in page)
        assertTrue("\"@type\":\"Person\"" in page)
        assertTrue("script.src = \"composeApp.js?v=949ee26\";" in page)
    }

    @Test
    fun pageRejectsUnknownPlaceholders() {
        assertFailsWith<IllegalArgumentException> { StaticHtml.page(portfolio, "{{nope}}", "949ee26") }
    }

    /** The build id lands in a URL query inside a script string, so only what can break neither is accepted. */
    @Test
    fun pageRejectsABuildIdThatCouldBreakTheScriptUrl() {
        listOf("", "a b", "x\" + alert(1) + \"", "a&b=c", "1#2", "v/1", "a?b").forEach { build ->
            assertFailsWith<IllegalArgumentException>("accepted \"$build\"") { StaticHtml.page(portfolio, "{{build}}", build) }
        }
        listOf("949ee26", "20261023T170200Z", "v1.2.3-rc.1_x").forEach { build ->
            assertEquals(build, StaticHtml.page(portfolio, "{{build}}", build))
        }
    }

    @Test
    fun jsonLdCannotCloseTheScriptTag() {
        val hostile = portfolio.copy(profile = portfolio.profile.copy(summary = "</script><b>"))
        val page = StaticHtml.page(hostile, "<script>{{jsonLd}}</script>", "949ee26")
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
    fun disclaimerBadgesTheProjectAndFollowsItsSummaryEscaped() {
        assertFalse("class=\"disclaimer\"" in StaticHtml.content(portfolio))

        val project = portfolio.projects[1].copy(disclaimer = "Not <their> app", visibility = RepoVisibility.Private)
        val article = StaticHtml.content(portfolio.copy(projects = listOf(project))).substringAfter("<article id=\"project-two\">")
        assertTrue("<span class=\"badge\">Private repo</span> <span class=\"badge\">${Project.UNOFFICIAL_BADGE}</span>" in article)
        assertTrue("<p>S</p>\n<p class=\"disclaimer\">Not &lt;their&gt; app</p>" in article, "right under the summary, escaped")
    }

    /**
     * With a backend named, the hero gets its live line, and the Contact section the published notes' place and
     * the note form; all three carry the backend's address for the page's script.
     */
    @Test
    fun theNoteFormTheNotesAndTheLiveLineAppearOnlyWithABackendAndPointToIt() {
        val none = StaticHtml.content(portfolio)
        assertFalse("<form" in none)
        assertFalse("data-api" in none)
        assertFalse("live-stats" in none)

        val html = StaticHtml.content(portfolio.copy(api = "https://api.example/\"x"))
        val contact = html.substringAfter("<section id=\"contact\">").substringBefore("</section>")
        assertTrue("<h3 id=\"feedback\">${FeedbackCopy.TITLE} <span class=\"badge\">${FeedbackCopy.PUBLIC}</span></h3>" in contact)
        assertTrue(
            "<form class=\"feedback\" method=\"post\" action=\"https://api.example/&quot;x/feedback\" data-api=\"https://api.example/&quot;x\">" in contact,
            "the form posts to the backend, escaped",
        )
        listOf(
            "<input name=\"name\" maxlength=\"80\" autocomplete=\"name\">",
            "<input name=\"contact\" type=\"email\" maxlength=\"160\" autocomplete=\"email\">",
            "<select name=\"rating\"><option value=\"\">No rating</option><option value=\"5\">5 · Excellent</option>",
            "<option value=\"1\">1 · Poor</option></select>",
            "<textarea name=\"message\" required maxlength=\"2000\" rows=\"5\"></textarea>",
            "<input type=\"hidden\" name=\"consent\" value=\"1\">",
            "<label class=\"trap\" aria-hidden=\"true\"><span>Website</span><input name=\"website\" tabindex=\"-1\" autocomplete=\"off\"></label>",
            "<button type=\"submit\">${FeedbackCopy.SEND}</button>",
            "<p class=\"status\" role=\"status\" aria-live=\"polite\"></p>",
        ).forEach { assertTrue(it in contact, "the form lacks $it") }
        val hero = html.substringBefore("</header>")
        assertTrue(
            "<p class=\"headline\">Engineer</p>\n<p class=\"live-stats muted\" data-api=\"https://api.example/&quot;x\" hidden></p>" in hero,
            "the live line sits right under the headline, hidden until the backend answers",
        )
        val notesBox = "<div class=\"visitor-notes\" data-api=\"https://api.example/&quot;x\" hidden>\n<h3>${FeedbackCopy.NOTES_TITLE}</h3>\n" +
            "<div class=\"ratings\" role=\"group\" aria-label=\"${FeedbackCopy.DASHBOARD}\" hidden>"
        assertTrue(notesBox in contact, "the notes' place, the dashboard first, empty until the script fills it")
        assertTrue(
            "<p class=\"list-line\" role=\"status\" aria-live=\"polite\"></p>\n<ul class=\"notes\"></ul>\n" +
                "<button class=\"more\" type=\"button\" hidden>${FeedbackCopy.MORE}</button>\n<dialog class=\"star-sheet\"" in contact,
            "what the list shows, the list, and the next page's button, then the star sheet",
        )
        assertFalse("type=\"checkbox\" name=\"consent\"" in contact, "every note is public, as the form says: no box to tick")
        assertTrue(contact.indexOf(notesBox) < contact.indexOf("<form class=\"feedback\""), "the notes come before the form")
        assertFalse("<footer" in html, "the count moved from the footer to the hero")
    }

    /**
     * Each dashboard bar opens its star's sheet, a dialog, rather than filtering the list in place: it says so to
     * screen readers, and nothing about it is a toggle any more.
     */
    @Test
    fun theBarsOpenTheStarSheet() {
        val html = StaticHtml.content(portfolio.copy(api = "https://api.example"))
        val bars = html.substringAfter("<ul class=\"bars\">").substringBefore("</ul>")
        (5 downTo 1).forEach {
            assertTrue(
                "<li><button type=\"button\" data-stars=\"$it\" aria-haspopup=\"dialog\" aria-label=\"${FeedbackCopy.openSheet(it)}\">" +
                    "<span class=\"label\">$it <i>★</i></span>" in bars,
                "the bar for $it stars",
            )
        }
        assertEquals(5, Regex("aria-haspopup=\"dialog\"").findAll(html).count(), "only the five bars open the sheet")
        assertFalse("aria-pressed" in bars, "a bar is no longer a filter")
        assertFalse("filtering" in html)
    }

    /**
     * The sheet is on the page already, empty, for the script to fill and open: a labelled dialog with the handle,
     * the head (stars, title, summary) and its close button, the sorter (All, then every relation, each with a
     * count), the list, "Show more", the empty line and the private line. It sits in the notes' place, after the list.
     */
    @Test
    fun theStarSheetIsOnThePageReadyToFill() {
        val html = StaticHtml.content(portfolio.copy(api = "https://api.example"))
        val notes = html.substringAfter("<div class=\"visitor-notes\"").substringBefore("<h3 id=\"feedback\">")
        val sheet = notes.substringAfter("<dialog ", missingDelimiterValue = "").substringBefore("</dialog>")
        assertTrue(sheet.startsWith("class=\"star-sheet\" aria-labelledby=\"star-sheet-title\">"), "a labelled dialog: ${sheet.take(80)}")
        assertTrue("<h4 id=\"star-sheet-title\"></h4>" in sheet, "the title it is labelled by")
        assertTrue(notes.indexOf("<ul class=\"notes\">") < notes.indexOf("<dialog"), "the page's list comes first")
        assertTrue(notes.trimEnd().endsWith("</dialog>\n</div>"), "the sheet closes the notes' place")
        val order = listOf(
            "<span class=\"handle\" aria-hidden=\"true\"></span>",
            "<p class=\"sheet-stars\" aria-hidden=\"true\"></p>",
            "<h4 id=\"star-sheet-title\"></h4>",
            "<p class=\"summary\"></p>",
            "<button class=\"close\" type=\"button\" aria-label=\"${FeedbackCopy.CLOSE}\"><svg",
            "<div class=\"relations\" role=\"group\" aria-label=\"",
            "<button type=\"button\" data-relation=\"\" aria-pressed=\"true\">${FeedbackCopy.ALL_RELATIONS}<span class=\"n\"></span></button>",
            "<ul class=\"notes\"></ul>",
            "<p class=\"empty\" role=\"status\" hidden></p>",
            "<button class=\"more\" type=\"button\" hidden>${FeedbackCopy.MORE}</button>",
            "<p class=\"private-line\" hidden></p>",
        )
        val positions = order.map { sheet.indexOf(it) }
        assertTrue(positions.none { it < 0 } && positions == positions.sorted(), "order ${order.zip(positions)}")
        val chips = sheet.substringAfter("<div class=\"relations\"").substringBefore("</div>")
        assertEquals(
            listOf("", "colleague", "friend", "special", "other"),
            Regex("data-relation=\"([a-z]*)\"").findAll(chips).map { it.groupValues[1] }.toList(),
            "All, then every relation in the model's order",
        )
        Relation.entries.forEach {
            assertTrue(
                "<button type=\"button\" data-relation=\"${it.wire}\" aria-pressed=\"false\">${StaticHtml.escape(it.plural)}<span class=\"n\"></span></button>" in chips,
                "the ${it.wire} chip, with a slot for its count",
            )
        }
        assertEquals(1, Regex("aria-pressed=\"true\"").findAll(chips).count(), "one pick at a time, All first")
    }

    /** The note form asks how the visitor knows the owner, optionally, with a radio button for each relation. */
    @Test
    fun theNoteFormAsksHowTheyKnowMe() {
        val html = StaticHtml.content(portfolio.copy(api = "https://api.example"))
        val form = html.substringAfter("<form class=\"feedback\"").substringBefore("</form>")
        val fieldset = form.substringAfter("<fieldset class=\"relations\">", missingDelimiterValue = "").substringBefore("</fieldset>")
        assertTrue(fieldset.startsWith("<legend>${StaticHtml.escape(FeedbackCopy.RELATION)} <span class=\"muted\">(optional)</span></legend>"), fieldset)
        assertEquals(
            Relation.entries.joinToString("") {
                "<label class=\"relation\"><input type=\"radio\" name=\"relation\" value=\"${it.wire}\"><span>${StaticHtml.escape(it.label)}</span></label>"
            },
            fieldset.substringAfter("</legend>"),
            "colleague, friend, special and other, none picked",
        )
        assertEquals(listOf("colleague", "friend", "special", "other"), Relation.entries.map { it.wire })
        assertFalse("checked" in fieldset || "required" in fieldset, "not saying is fine")
        assertTrue(form.indexOf("name=\"contact\"") < form.indexOf("<fieldset") && form.indexOf("<fieldset") < form.indexOf("name=\"rating\""))
    }

    /** Without a backend there is nothing to rate, read or send: no bars, no sheet, no relation to give. */
    @Test
    fun theSheetAndTheRelationsNeedABackend() {
        val html = StaticHtml.content(portfolio)
        listOf("<dialog", "star-sheet", "aria-haspopup", "data-relation", "name=\"relation\"", "class=\"relations\"", FeedbackCopy.RELATION)
            .forEach { assertFalse(it in html, "$it without a backend") }
    }

    /**
     * The private message form posts to the backend without the page's script too, marks itself private against
     * the note form's public, and comes before everything public in the Contact section.
     */
    @Test
    fun theMessageFormAppearsOnlyWithABackendAndPostsToIt() {
        assertFalse("form class=\"message\"" in StaticHtml.content(portfolio))
        assertFalse("id=\"message\"" in StaticHtml.content(portfolio))

        val html = StaticHtml.content(portfolio.copy(api = "https://api.example/\"x"))
        val contact = html.substringAfter("<section id=\"contact\">").substringBefore("</section>")
        assertTrue("<h3 id=\"message\">${MessageCopy.TITLE} <span class=\"badge\">${MessageCopy.PRIVATE}</span></h3>" in contact)
        assertTrue("<p class=\"note\">${StaticHtml.escape(MessageCopy.INTRO)}</p>" in contact)
        val form = contact.substringAfter("<form class=\"message\"").substringBefore("</form>")
        assertTrue(
            "<form class=\"message\" method=\"post\" action=\"https://api.example/&quot;x/messages\" data-api=\"https://api.example/&quot;x\">" in contact,
            "the form posts to the backend, escaped",
        )
        listOf(
            "<input name=\"name\" maxlength=\"80\" autocomplete=\"name\">",
            "<span>${MessageCopy.CONTACT} <span class=\"muted\">(required: ${MessageCopy.CONTACT_HINT})</span></span>" +
                "<input name=\"contact\" required maxlength=\"160\" autocomplete=\"email\">",
            "<fieldset class=\"topics\"><legend>${StaticHtml.escape(MessageCopy.TOPIC)} <span class=\"muted\">(optional)</span></legend>",
            "<label class=\"topic\"><input type=\"radio\" name=\"topic\" value=\"job\"><span>A job</span></label>" +
                "<label class=\"topic\"><input type=\"radio\" name=\"topic\" value=\"freelance\"><span>A freelance project</span></label>" +
                "<label class=\"topic\"><input type=\"radio\" name=\"topic\" value=\"collaboration\"><span>Collaboration</span></label>" +
                "<label class=\"topic\"><input type=\"radio\" name=\"topic\" value=\"other\"><span>Something else</span></label></fieldset>",
            "<textarea name=\"message\" required maxlength=\"2000\" rows=\"5\"></textarea>",
            "<label class=\"trap\" aria-hidden=\"true\"><span>Website</span><input name=\"website\" tabindex=\"-1\" autocomplete=\"off\"></label>",
            "<button type=\"submit\">${MessageCopy.SEND}</button>",
            "<p class=\"status\" role=\"status\" aria-live=\"polite\"></p>",
        ).forEach { assertTrue(it in form, "the message form lacks $it") }
        assertFalse("consent" in form || "rating" in form, "nothing in it is ever shown, so there is nothing to agree to")
        assertTrue(
            contact.indexOf("<form class=\"message\"") in 0 until contact.indexOf("class=\"visitor-notes\""),
            "the private form comes before the published notes and the note form",
        )
    }

    /** With a backend, the profile's LinkedIn and GitHub links name themselves for the page's click beacon. */
    @Test
    fun theTrackedLinksCarryTheirNameOnlyWithABackend() {
        val links = portfolio.copy(
            links = listOf(Link("LinkedIn", "https://www.linkedin.com/in/ada"), Link("GitHub", "https://github.com/ada"), Link("Blog", "https://ada.dev")),
            projects = listOf(portfolio.projects[0].copy(links = listOf(Link("Source on GitHub", "https://github.com/ada/one")))),
        )
        val without = StaticHtml.content(links)
        assertFalse("data-track" in without)
        assertTrue("<a href=\"https://www.linkedin.com/in/ada\" rel=\"noopener\">LinkedIn</a>" in without)

        val html = StaticHtml.content(links.copy(api = "https://api.example"))
        val contact = html.substringAfter("<section id=\"contact\">").substringBefore("</section>")
        assertTrue(
            "<a href=\"https://www.linkedin.com/in/ada\" rel=\"noopener\" data-track=\"linkedin\" data-api=\"https://api.example\">LinkedIn</a>" in contact,
        )
        assertTrue("<a href=\"https://github.com/ada\" rel=\"noopener\" data-track=\"github\" data-api=\"https://api.example\">GitHub</a>" in contact)
        assertTrue("<a href=\"https://ada.dev\" rel=\"noopener\">Blog</a>" in contact, "a link the backend does not count")
        assertEquals(2, Regex("data-track=").findAll(html).count(), "only the profile's two, not a project's source link")
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
