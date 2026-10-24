package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.model.Engineering
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.MessageCopy
import dev.yowkey.portfolio.model.MessageDraft
import dev.yowkey.portfolio.model.MessageTopic
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.Relation
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.model.trackingKey
import dev.yowkey.portfolio.ui.Section
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.yowkey.portfolio.ui.components.initialsOf
import dev.yowkey.portfolio.ui.components.roleOf
import dev.yowkey.portfolio.ui.components.shorterForms

/**
 * The crawlable HTML twin of the Compose UI. Search engines, link previews and browsers without WasmGC read this;
 * everyone else sees it until the Compose app has loaded.
 */
object StaticHtml {

    /**
     * Fills the `{{placeholders}}` of web/index.template.html. [build] is the id of this build (the git commit, from the
     * Gradle task): the template puts it in the app script's URL, `composeApp.js?v=<build>`, so a freshly deployed
     * index.html never loads the previous deploy's script out of the Pages cache. It sits in a URL query inside a
     * script string, so only letters, digits, `.`, `_` and `-` are accepted; anything else fails the build.
     */
    fun page(portfolio: Portfolio, template: String, build: String): String {
        require(BUILD_ID.matches(build)) { "the build id must be letters, digits, '.', '_' or '-', not \"$build\"" }
        val profile = portfolio.profile
        val values = mapOf(
            "title" to escape("${profile.name} — ${profile.headline}"),
            "description" to escape(profile.summary),
            "url" to escape(portfolio.siteUrl),
            "name" to escape(profile.name),
            "jsonLd" to jsonLd(portfolio),
            "bar" to bar(portfolio),
            "content" to content(portfolio),
            "toTop" to backToTop(),
            "build" to build,
        )
        val unknown = Regex("\\{\\{(\\w+)}}").findAll(template).map { it.groupValues[1] }.toSet() - values.keys
        require(unknown.isEmpty()) { "index.template.html uses unknown placeholders: $unknown" }
        return values.entries.fold(template) { html, (key, value) -> html.replace("{{$key}}", value) }
    }

    fun sitemap(portfolio: Portfolio): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
        |  <url><loc>${escape(portfolio.siteUrl)}</loc></url>
        |</urlset>
        |""".trimMargin()

    fun robots(portfolio: Portfolio): String = "User-agent: *\nAllow: /\nSitemap: ${portfolio.siteUrl}sitemap.xml\n"

    /**
     * The sticky bar over the page, as the app's: the monogram, which is the Home link; the brand slot; a link to each
     * section the page has, with the app's names; the theme switch and the menu; and the reading progress along its
     * bottom edge. The hero already shows the name in huge type, so while it is on screen the slot shows the proof
     * chip ([proofChip]: the notes' rating and the visit count, a link to the notes), and only once the hero's name
     * has scrolled under the bar the name over the role. The chip is the only part of the bar that needs the
     * backend, so without an `api` there is none.
     *
     * Without the page's script it still works as plain HTML: the links and the monogram are anchors (`#top` is the
     * empty element ahead of the bar, so the jump lands on the page's very top), the slot stays empty (the chip has
     * nothing to show until the script has the backend's numbers), and the switch and the menu, which only the
     * script can work, are not drawn. The script adds the rest: the chip, the swap, the link to the section being
     * read, the switch, the menu on narrow screens, and the progress where CSS cannot draw it alone.
     */
    fun bar(portfolio: Portfolio): String = buildString {
        val p = portfolio.profile
        appendLine("<div id=\"top\"></div>")
        appendLine("<header class=\"bar\">")
        appendLine("<div class=\"bar-row\">")
        appendLine(
            "<a class=\"mark\" href=\"#top\" aria-label=\"${escape("${p.name}, back to top")}\">" +
                "<span aria-hidden=\"true\">${escape(initialsOf(p.name))}</span></a>",
        )
        append("<div class=\"brand\">")
        portfolio.api?.let { proofChip(it) }
        val role = roleOf(p.headline)
        append("<span class=\"who\"><strong>${escape(p.name)}</strong><span aria-label=\"${escape(role)}\">${fitted(role, ROLE_SHORT)}</span></span>")
        appendLine("</div>")
        appendLine("<nav class=\"sections\" id=\"sections\" aria-label=\"Sections\"><ul>")
        sections(portfolio).forEach { appendLine("<li><a href=\"#${it.name.lowercase()}\">${escape(it.title)}</a></li>") }
        appendLine("</ul></nav>")
        appendLine(
            "<button class=\"theme\" type=\"button\" role=\"switch\" aria-checked=\"false\" aria-label=\"Dark theme\">" +
                "<span class=\"knob\"></span>$SUN_ICON$MOON_ICON</button>",
        )
        appendLine(
            "<button class=\"menu\" type=\"button\" aria-expanded=\"false\" aria-controls=\"sections\" " +
                "aria-label=\"Open navigation menu\">$MENU_ICON</button>",
        )
        appendLine("</div>")
        appendLine("<span class=\"progress\" aria-hidden=\"true\"></span>")
        appendLine("</header>")
    }

    /**
     * The round button that takes the reader back up. The page's script shows it once they are about a screen and a
     * half down; without the script it never shows, and the bar's monogram does the same job as a plain link.
     */
    fun backToTop(): String = "<button class=\"to-top\" type=\"button\" aria-label=\"Back to top\">$UP_ICON</button>"

    /**
     * The sections [content] renders, in its order, which is the app's ([Section]): the bar links to each by its id
     * (the section's name in lower case, `<section id="experience">`) under the title the app's bar gives it.
     */
    private fun sections(portfolio: Portfolio): List<Section> = listOfNotNull(
        Section.Experience.takeIf { portfolio.experience.isNotEmpty() },
        Section.Recognition.takeIf { portfolio.recognition.isNotEmpty() },
        Section.Projects.takeIf { portfolio.projects.isNotEmpty() },
        Section.Skills.takeIf { portfolio.skills.isNotEmpty() },
        Section.Services.takeIf { portfolio.services.isNotEmpty() },
        Section.Contact,
    )

    /**
     * The proof chip, as the app's: on the left the notes' rating (five small stars lit to the fraction, the average
     * in bold, then "· 1 rating" in muted type), a hairline divider, on the right an eye and the visit total ("1,234",
     * bold, then "visits"). It links to the notes' dashboard (`#notes`, [visitorNotes]). Here it is only the empty
     * structure, hidden: the page's script fetches the numbers from the backend at [api], fills the words and the
     * label ("Rated 5.0 out of 5 from 1 rating, 35 visits. Read the reviews."), hides a part that has nothing to
     * show, fades the chip in and counts its numbers up once. Without the script, or until the backend answers, the
     * slot stays empty; with nothing rated and nothing counted, too.
     */
    private fun StringBuilder.proofChip(api: String) {
        append("<a class=\"proof\" href=\"#notes\" data-api=\"${escape(api)}\" hidden>")
        append("<span class=\"rate\"><span class=\"meter\" aria-hidden=\"true\"></span><b class=\"avg\"></b><span class=\"words\"></span></span>")
        append("<span class=\"sep\" aria-hidden=\"true\"></span>")
        append("<span class=\"seen\">$EYE_ICON<b class=\"visits\"></b><span class=\"words\"></span></span>")
        append("</a>")
    }

    /**
     * [text] whole, and for a phone's bar its longest whole-word form of at most [shortChars] characters (the role
     * without its "since 2024"), the same cut the app's bar makes (`shorterForms`); CSS shows one or the other. Both
     * are hidden from screen readers, which hear the whole text from the element's label.
     */
    private fun fitted(text: String, shortChars: Int): String {
        val short = shorterForms(text).firstOrNull { it.length <= shortChars } ?: text
        return "<span class=\"full\" aria-hidden=\"true\">${escape(text)}</span>" +
            "<span class=\"short\" aria-hidden=\"true\">${escape(short)}</span>"
    }

    /** The phone bar's room, in characters, for the role. */
    private const val ROLE_SHORT = 30

    /** The chip's eye, for the visits: an almond and its pupil, drawn in the text's colour. */
    private const val EYE_ICON = "<svg class=\"eye\" width=\"15\" height=\"15\" viewBox=\"0 0 16 16\" aria-hidden=\"true\" focusable=\"false\">" +
        "<path d=\"M1.3 8C2.9 5.1 5.3 3.6 8 3.6s5.1 1.5 6.7 4.4c-1.6 2.9-4 4.4-6.7 4.4S2.9 10.9 1.3 8z\" fill=\"none\" " +
        "stroke=\"currentColor\" stroke-width=\"1.5\" stroke-linejoin=\"round\"/><circle cx=\"8\" cy=\"8\" r=\"2.2\" fill=\"currentColor\"/></svg>"

    /** The switch's two ends, as the app's: the sun on the left, the moon on the right; the knob covers the one that is on. */
    private const val SUN_ICON = "<svg class=\"sun\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\" aria-hidden=\"true\" focusable=\"false\">" +
        "<circle cx=\"8\" cy=\"8\" r=\"3\" fill=\"currentColor\"/><path d=\"M8 1.2v1.6M8 13.2v1.6M1.2 8h1.6M13.2 8h1.6" +
        "M3.2 3.2l1.1 1.1M11.7 11.7l1.1 1.1M3.2 12.8l1.1-1.1M11.7 4.3l1.1-1.1\" stroke=\"currentColor\" " +
        "stroke-width=\"1.5\" stroke-linecap=\"round\"/></svg>"

    private const val MOON_ICON = "<svg class=\"moon\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\" aria-hidden=\"true\" focusable=\"false\">" +
        "<path d=\"M13.6 10.1A5.9 5.9 0 0 1 5.9 2.4a5.9 5.9 0 1 0 7.7 7.7z\" fill=\"currentColor\"/></svg>"

    /** The hero portrait's trail, as the app's: seven echoes, farthest first. */
    private const val PORTRAIT_ECHOES = 7

    /**
     * Noise that varies only down the page, pushing each row of an echo sideways (its vertical push is zeroed), so the
     * outline ripples as the app's waves do; three strengths for the trail's middle, far and farthest echoes. A single
     * octave of noise stays near its middle, hence scales several times the app's wave depths. In sRGB, so the zeroed
     * channel's 0.5 stays the no-push middle.
     */
    private val MELT_FILTERS = "<svg class=\"melt\" width=\"0\" height=\"0\" aria-hidden=\"true\" focusable=\"false\">" +
        listOf(1 to 80, 2 to 150, 3 to 220).joinToString("") { (n, scale) ->
            "<filter id=\"melt-$n\" color-interpolation-filters=\"sRGB\" x=\"-20%\" y=\"0\" width=\"140%\" height=\"100%\">" +
                "<feTurbulence type=\"fractalNoise\" baseFrequency=\"0 0.012\" numOctaves=\"1\" seed=\"$n\" result=\"noise\"/>" +
                "<feColorMatrix in=\"noise\" result=\"rows\" type=\"matrix\" values=\"1 0 0 0 0 0 0 0 0 0.5 0 0 0 0 0 0 0 0 0 1\"/>" +
                "<feDisplacementMap in=\"SourceGraphic\" in2=\"rows\" scale=\"$scale\" xChannelSelector=\"R\" yChannelSelector=\"G\"/></filter>"
        } + "</svg>"

    /** Three lines, as the app's MenuGlyph; its name is the button's label. */
    private const val MENU_ICON = "<svg width=\"20\" height=\"20\" viewBox=\"0 0 20 20\" aria-hidden=\"true\" focusable=\"false\">" +
        "<path d=\"M2 5h16M2 10h16M2 15h16\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"/></svg>"

    private const val UP_ICON = "<svg width=\"20\" height=\"20\" viewBox=\"0 0 20 20\" aria-hidden=\"true\" focusable=\"false\">" +
        "<path d=\"M10 16V4.5M4.5 10L10 4.5l5.5 5.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" " +
        "stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>"

    /** The semantic body: one `<section>` per part of the page, in the same order as the Compose UI (`Section`). */
    fun content(portfolio: Portfolio): String = buildString {
        val p = portfolio.profile
        appendLine("<header class=\"hero\">")
        // The app's still picture: the echoes are the cut-out again, as CSS masks filled with the accent, the far ones
        // melted by the SVG filters.
        p.photoPath?.let { photo ->
            val url = resourceUrl(photo)
            appendLine("<figure class=\"portrait\" style=\"--photo: url('$url')\">")
            appendLine(MELT_FILTERS)
            repeat(PORTRAIT_ECHOES) { appendLine("<span class=\"echo\"><i></i></span>") }
            appendLine("<span class=\"person\"><img src=\"$url\" alt=\"Portrait of ${escape(p.name)}\" decoding=\"async\"></span>")
            appendLine("</figure>")
        }
        appendLine("<h1>${escape(p.name)}</h1>")
        appendLine("<p class=\"headline\">${escape(p.headline)}</p>")
        // The rating and the visit count are the bar's (its proof chip), so the hero does not repeat them.
        if (p.proof.isNotEmpty()) {
            appendLine("<ul class=\"proof\">")
            p.proof.forEach { appendLine("<li><strong>${escape(it.value)}</strong> ${escape(it.label)}</li>") }
            appendLine("</ul>")
        }
        links(listOfNotNull(p.email?.let { Link("Email me", "mailto:$it") }, Link("See projects", "#projects")))
        appendLine("<p>${escape(p.summary)}</p>")
        appendLine("</header>")

        experience(portfolio)
        recognition(portfolio)

        if (portfolio.projects.isNotEmpty()) {
            appendLine("<section id=\"projects\"><h2>Projects</h2>")
            portfolio.projectGroups.forEach { (group, members) ->
                appendLine("<div class=\"project-group\" id=\"projects-${group.name.lowercase()}\">")
                appendLine("<h3 class=\"group\">${escape(group.title)}</h3>")
                group.note(members)?.let { appendLine("<p class=\"note\">${escape(it)}</p>") }
                members.forEach { project(it) }
                appendLine("</div>")
            }
            appendLine("</section>")
        }

        if (portfolio.skills.isNotEmpty()) {
            appendLine("<section id=\"skills\"><h2>Skills</h2>")
            portfolio.skills.forEach { group ->
                appendLine("<h3>${escape(group.name)}</h3>")
                appendLine("<p>${group.items.joinToString(" · ") { escape(it) }}</p>")
            }
            appendLine("</section>")
        }

        if (portfolio.services.isNotEmpty()) {
            appendLine("<section id=\"services\"><h2>Focus areas</h2><ul class=\"cards\">")
            portfolio.services.forEach {
                appendLine("<li><h3>${escape(it.title)}</h3><p>${escape(it.description)}</p></li>")
            }
            appendLine("</ul></section>")
        }

        appendLine("<section id=\"contact\"><h2>Contact</h2>")
        links(listOfNotNull(p.email?.let { Link("Email", "mailto:$it") }) + portfolio.links, tracksClicksAt = portfolio.api)
        // As in the app: the private form first, then everything public (the published notes, the note form).
        portfolio.api?.let {
            messageForm(it)
            visitorNotes(it)
            feedbackForm(it)
        }
        appendLine("</section>")
    }

    /**
     * The private message form, as the app's: name (optional), how to reach you (required), the topic as four radio
     * buttons (none picked is filed as "Something else"), the message. It posts to the backend at [api] like the note
     * form: through the page's script, or as a plain form the backend answers with a page. The `website` trap is the
     * note form's too.
     */
    private fun StringBuilder.messageForm(api: String) {
        appendLine("<h3 id=\"message\">${escape(MessageCopy.TITLE)} <span class=\"badge\">${escape(MessageCopy.PRIVATE)}</span></h3>")
        appendLine("<p class=\"note\">${escape(MessageCopy.INTRO)}</p>")
        appendLine("<form class=\"message\" method=\"post\" action=\"${escape("$api/messages")}\" data-api=\"${escape(api)}\">")
        appendLine("<div class=\"fields\">")
        appendLine(
            "<label><span>${escape(MessageCopy.NAME)} <span class=\"muted\">(optional)</span></span>" +
                "<input name=\"name\" maxlength=\"${MessageDraft.MAX_NAME}\" autocomplete=\"name\"></label>",
        )
        appendLine(
            "<label><span>${escape(MessageCopy.CONTACT)} <span class=\"muted\">(required: ${escape(MessageCopy.CONTACT_HINT)})</span></span>" +
                "<input name=\"contact\" required maxlength=\"${MessageDraft.MAX_CONTACT}\" autocomplete=\"email\"></label>",
        )
        appendLine("</div>")
        append("<fieldset class=\"topics\"><legend>${escape(MessageCopy.TOPIC)} <span class=\"muted\">(optional)</span></legend>")
        MessageTopic.entries.forEach {
            append("<label class=\"topic\"><input type=\"radio\" name=\"topic\" value=\"${it.wire}\"><span>${escape(it.label)}</span></label>")
        }
        appendLine("</fieldset>")
        appendLine(
            "<label><span>${escape(MessageCopy.MESSAGE)}</span>" +
                "<textarea name=\"message\" required maxlength=\"${MessageDraft.MAX_MESSAGE}\" rows=\"5\"></textarea></label>",
        )
        appendLine("<label class=\"trap\" aria-hidden=\"true\"><span>Website</span><input name=\"website\" tabindex=\"-1\" autocomplete=\"off\"></label>")
        appendLine("<button type=\"submit\">${escape(MessageCopy.SEND)}</button>")
        appendLine("<p class=\"status\" role=\"status\" aria-live=\"polite\"></p>")
        appendLine("</form>")
    }

    /**
     * Where the page's script shows the notes, from the backend; hidden until it answers. The ratings dashboard is
     * here already, empty, for the script to fill: the average and its stars, and a bar per number of stars, each a
     * button that opens that star's sheet. Then the line saying what the list shows, the list (pinned notes first,
     * as the backend orders it), and "Show more"; last, the sheet itself. Its id, `notes`, is where the bar's proof
     * chip jumps to; like every id in the content, the jump stops below the bar.
     */
    private fun StringBuilder.visitorNotes(api: String) {
        appendLine("<div class=\"visitor-notes\" id=\"notes\" data-api=\"${escape(api)}\" hidden>")
        appendLine("<h3>${escape(FeedbackCopy.NOTES_TITLE)}</h3>")
        appendLine("<div class=\"ratings\" role=\"group\" aria-label=\"${escape(FeedbackCopy.DASHBOARD)}\" hidden>")
        appendLine("<div class=\"average\"><p class=\"score\"><strong></strong><span>/ 5</span></p><p class=\"meter\" role=\"img\"></p><p class=\"count\"></p></div>")
        append("<ul class=\"bars\">")
        (5 downTo 1).forEach {
            // The script adds the counts to the label once it has them.
            append(
                "<li><button type=\"button\" data-stars=\"$it\" aria-haspopup=\"dialog\" aria-label=\"${escape(FeedbackCopy.openSheet(it))}\">" +
                    "<span class=\"label\">$it <i>★</i></span><span class=\"track\"><span class=\"fill\"></span></span>" +
                    "<span class=\"n\"></span><span class=\"pct\"></span></button></li>",
            )
        }
        appendLine("</ul>")
        appendLine("</div>")
        appendLine("<p class=\"kept-private\" hidden></p>")
        appendLine("<p class=\"list-line\" role=\"status\" aria-live=\"polite\"></p>")
        appendLine("<ul class=\"notes\"></ul>")
        appendLine("<button class=\"more\" type=\"button\" hidden>${escape(FeedbackCopy.MORE)}</button>")
        starSheet()
        appendLine("</div>")
    }

    /**
     * A star's notes, as a bottom sheet the dashboard's bars open (a modal `<dialog>`): the handle, the stars and the
     * title with what they add up to, the close button, then the sorter (All and each [Relation], with its count), the
     * list, "Show more", the line for the ratings whose words are private, and the line for a pick with nothing to
     * read. Only the page's script opens it, and fills it from the backend; it is here empty so the script never
     * builds structure, only words. The head stays put while the body scrolls under it.
     */
    private fun StringBuilder.starSheet() {
        appendLine("<dialog class=\"star-sheet\" aria-labelledby=\"star-sheet-title\">")
        appendLine("<div class=\"sheet-top\">")
        appendLine("<span class=\"handle\" aria-hidden=\"true\"></span>")
        appendLine(
            "<div class=\"sheet-head\"><div><p class=\"sheet-stars\" aria-hidden=\"true\"></p><h4 id=\"star-sheet-title\"></h4>" +
                "<p class=\"summary\"></p></div>" +
                "<button class=\"close\" type=\"button\" aria-label=\"${escape(FeedbackCopy.CLOSE)}\">$CLOSE_ICON</button></div>",
        )
        append("<div class=\"relations\" role=\"group\" aria-label=\"${escape(SORTER_LABEL)}\">")
        append("<button type=\"button\" data-relation=\"\" aria-pressed=\"true\">${escape(FeedbackCopy.ALL_RELATIONS)}<span class=\"n\"></span></button>")
        Relation.entries.forEach {
            append("<button type=\"button\" data-relation=\"${it.wire}\" aria-pressed=\"false\">${escape(it.plural)}<span class=\"n\"></span></button>")
        }
        appendLine("</div>")
        appendLine("</div>")
        appendLine("<div class=\"sheet-body\">")
        appendLine("<ul class=\"notes\"></ul>")
        appendLine("<p class=\"empty\" role=\"status\" hidden></p>")
        appendLine("<button class=\"more\" type=\"button\" hidden>${escape(FeedbackCopy.MORE)}</button>")
        appendLine("<p class=\"private-line\" hidden></p>")
        appendLine("</div>")
        appendLine("</dialog>")
    }

    /** What the sorter's chips are, for screen readers: they pick the notes by how their writers know the owner. */
    private const val SORTER_LABEL = "Notes by how they know me"

    /** The close button's cross; its name is the button's label. */
    private const val CLOSE_ICON = "<svg width=\"18\" height=\"18\" viewBox=\"0 0 18 18\" aria-hidden=\"true\" focusable=\"false\">" +
        "<path d=\"M4 4l10 10M14 4L4 14\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"/></svg>"

    /**
     * The note form, as the app's: name and email (optional), how they know the owner as four radio buttons (optional,
     * none picked sends nothing), the impression, the message; public, as it says, so it sends the consent itself (a
     * hidden field, for the plain form too). It posts to the backend at [api] the way the app does, through the page's
     * script, the relation included when one is picked; without the script the browser posts the plain
     * form and the backend answers with a page. The `website` field is a trap for bots that fill every field:
     * people never see it, and a filled one is quietly dropped.
     */
    private fun StringBuilder.feedbackForm(api: String) {
        appendLine("<h3 id=\"feedback\">${escape(FeedbackCopy.TITLE)} <span class=\"badge\">${escape(FeedbackCopy.PUBLIC)}</span></h3>")
        appendLine("<p class=\"note\">${escape(FeedbackCopy.INTRO)}</p>")
        appendLine("<form class=\"feedback\" method=\"post\" action=\"${escape("$api/feedback")}\" data-api=\"${escape(api)}\">")
        appendLine("<div class=\"fields\">")
        appendLine(
            "<label><span>${escape(FeedbackCopy.NAME)} <span class=\"muted\">(optional)</span></span>" +
                "<input name=\"name\" maxlength=\"${FeedbackDraft.MAX_NAME}\" autocomplete=\"name\"></label>",
        )
        appendLine(
            "<label><span>${escape(FeedbackCopy.CONTACT)} <span class=\"muted\">(optional, if you&#39;d like a reply)</span></span>" +
                "<input name=\"contact\" type=\"email\" maxlength=\"${FeedbackDraft.MAX_CONTACT}\" autocomplete=\"email\"></label>",
        )
        appendLine("</div>")
        append("<fieldset class=\"relations\"><legend>${escape(FeedbackCopy.RELATION)} <span class=\"muted\">(optional)</span></legend>")
        Relation.entries.forEach {
            append("<label class=\"relation\"><input type=\"radio\" name=\"relation\" value=\"${it.wire}\"><span>${escape(it.label)}</span></label>")
        }
        appendLine("</fieldset>")
        append("<label><span>${escape(FeedbackCopy.RATING)} <span class=\"muted\">(optional)</span></span>")
        append("<select name=\"rating\"><option value=\"\">No rating</option>")
        (5 downTo 1).forEach { append("<option value=\"$it\">${escape(FeedbackCopy.ratingLabel(it))}</option>") }
        appendLine("</select></label>")
        appendLine(
            "<label><span>${escape(FeedbackCopy.MESSAGE)}</span>" +
                "<textarea name=\"message\" required maxlength=\"${FeedbackDraft.MAX_MESSAGE}\" rows=\"5\"></textarea></label>",
        )
        appendLine("<input type=\"hidden\" name=\"consent\" value=\"1\">")
        appendLine("<label class=\"trap\" aria-hidden=\"true\"><span>Website</span><input name=\"website\" tabindex=\"-1\" autocomplete=\"off\"></label>")
        appendLine("<button type=\"submit\">${escape(FeedbackCopy.SEND)}</button>")
        appendLine("<p class=\"status\" role=\"status\" aria-live=\"polite\"></p>")
        appendLine("</form>")
    }

    /**
     * One project, with everything the app splits between its card and its detail screen. The card's answers come
     * first, in the card's order (media, title, meta and badges, one line, summary and any disclaimer, figures, what
     * it is built with and runs on), so a crawler or a skim reads the same summary; the detail follows (impact, highlights, optimizations, gallery,
     * engineering, stack, links). Headings sit under the group's `<h3>`: the title is `<h4>`, after the app's icon
     * when it has one, and its parts `<h5>`.
     * The id is the app's deep link target too: `#/projects/<id>` scrolls here on this page.
     */
    private fun StringBuilder.project(project: Project) {
        appendLine("<article id=\"${escape(project.anchor)}\">")
        project.coverClip?.let { clip(it, "cover ${orientation(it)}") } ?: project.cover?.let { cover(it) }
        val title = "<h4>${escape(project.title)}</h4>"
        val icon = project.iconPath
        if (icon == null) {
            appendLine(title)
        } else {
            // Decorative (empty alt): the title beside it names the app.
            appendLine(
                "<div class=\"heading\"><img class=\"app-icon\" src=\"${resourceUrl(icon)}\" alt=\"\" width=\"40\" " +
                    "height=\"40\" loading=\"lazy\" decoding=\"async\">$title</div>",
            )
        }
        val meta = listOfNotNull(
            project.meta.takeIf { it.isNotEmpty() }?.let { escape(it) },
            project.visibility?.let { "<span class=\"badge\">${escape(it.badge)}</span>" },
            project.disclaimer?.let { "<span class=\"badge\">${escape(Project.UNOFFICIAL_BADGE)}</span>" },
        )
        if (meta.isNotEmpty()) appendLine("<p class=\"muted\">${meta.joinToString(" ")}</p>")
        project.tagline?.let { appendLine("<p class=\"tagline\">${escape(it)}</p>") }
        appendLine("<p>${escape(project.summary)}</p>")
        project.disclaimer?.let { appendLine("<p class=\"disclaimer\">${escape(it)}</p>") }
        if (project.figures.isNotEmpty()) {
            appendLine("<ul class=\"figures\">")
            project.figures.forEach { appendLine("<li><strong>${escape(it.value)}</strong> <span>${escape(it.label)}</span></li>") }
            appendLine("</ul>")
        }
        project.engineering?.let { facts(it) }
        if (project.isWork) {
            titledList("Impact", project.impact)
            titledList(project.highlightsTitle, project.highlights)
            titledList("Optimizations and audits", project.optimizations)
        } else {
            list(project.highlights)
        }
        // With a cover clip leading, the still cover joins the gallery, so every image the app shows is here.
        gallery(listOfNotNull(project.cover.takeIf { project.coverClip != null }), project.clips, project.screenshots)
        project.engineering?.let { engineering(it) }
        if (project.stack.isNotEmpty()) {
            appendLine("<p class=\"muted\">${project.stack.joinToString(" · ") { escape(it) }}</p>")
        }
        links(project.links)
        appendLine("</article>")
    }

    /** "Built with" and "Runs on", as a definition list right under the summary, where the card has its chips. */
    private fun StringBuilder.facts(engineering: Engineering) {
        val rows = listOf("Built with" to engineering.builtWith, "Runs on" to engineering.platforms).filter { it.second.isNotEmpty() }
        if (rows.isEmpty()) return
        appendLine("<dl class=\"facts\">")
        rows.forEach { (term, items) ->
            appendLine("<dt>$term</dt><dd>${items.joinToString(" · ") { escape(it) }}</dd>")
        }
        appendLine("</dl>")
    }

    /**
     * The detail screen's "How it's built": the architecture paragraph, the modules as a nested list (each module,
     * then what it depends on), the decisions as a definition list, and the quality checklist.
     */
    private fun StringBuilder.engineering(engineering: Engineering) {
        val hasBody = engineering.architecture != null || engineering.modules.isNotEmpty() ||
            engineering.decisions.isNotEmpty() || engineering.quality.isNotEmpty()
        if (!hasBody) return
        appendLine("<div class=\"engineering\">")
        appendLine("<h5>How it&#39;s built</h5>")
        engineering.architecture?.let { appendLine("<p>${escape(it)}</p>") }
        if (engineering.modules.isNotEmpty()) {
            appendLine("<h6>Modules</h6>")
            appendLine("<ul class=\"modules\">")
            engineering.modules.forEach { module ->
                append("<li><code>${escape(module.name)}</code> ${escape(module.role)}")
                if (module.dependsOn.isNotEmpty()) {
                    append("<ul>")
                    module.dependsOn.forEach { append("<li>depends on <code>${escape(it)}</code></li>") }
                    append("</ul>")
                }
                appendLine("</li>")
            }
            appendLine("</ul>")
        }
        if (engineering.decisions.isNotEmpty()) {
            appendLine("<h6>Key decisions</h6>")
            appendLine("<dl class=\"decisions\">")
            engineering.decisions.forEach { appendLine("<dt>${escape(it.title)}</dt><dd>${escape(it.detail)}</dd>") }
            appendLine("</dl>")
        }
        if (engineering.quality.isNotEmpty()) {
            appendLine("<h6>Quality</h6>")
            appendLine("<ul class=\"quality\">${engineering.quality.joinToString("") { "<li>${escape(it)}</li>" }}</ul>")
        }
        appendLine("</div>")
    }

    private fun orientation(shot: Screenshot) = if (shot.aspectRatio < 1f) "portrait" else "landscape"

    /** The project's splash screen first, as on the card: its hero-sized thumbnail, linking to the full image. */
    private fun StringBuilder.cover(cover: Screenshot) {
        appendLine(
            "<a href=\"${resourceUrl(cover.fullPath)}\"><img class=\"cover ${orientation(cover)}\" " +
                "src=\"${resourceUrl(cover.thumbPath)}\" ${imgAttributes(cover)}></a>",
        )
    }

    /**
     * A clip is the animated WebP itself, which every browser that runs this page animates natively; under reduced
     * motion the `<source>` swaps in its still poster. [imgClass] styles a cover clip like a cover.
     */
    private fun StringBuilder.clip(clip: Screenshot, imgClass: String? = null) {
        val classAttribute = imgClass?.let { "class=\"$it\" " }.orEmpty()
        appendLine(
            "<picture><source media=\"(prefers-reduced-motion: reduce)\" srcset=\"${resourceUrl(clip.posterPath)}\">" +
                "<img ${classAttribute}src=\"${resourceUrl(clip.fullPath)}\" ${imgAttributes(clip)}></picture>",
        )
    }

    /** Stills (thumbnails linking to the full image) for [leading], then clips, then screenshots. */
    private fun StringBuilder.gallery(leading: List<Screenshot>, clips: List<Screenshot>, shots: List<Screenshot>) {
        if (leading.isEmpty() && clips.isEmpty() && shots.isEmpty()) return
        appendLine("<div class=\"shots\">")
        fun still(shot: Screenshot) = appendLine(
            "<a href=\"${resourceUrl(shot.fullPath)}\"><img src=\"${resourceUrl(shot.thumbPath)}\" " +
                "${imgAttributes(shot)}></a>",
        )
        leading.forEach(::still)
        clips.forEach { clip(it) }
        shots.forEach(::still)
        appendLine("</div>")
    }

    /** Alt text and the intrinsic size, so the browser reserves the space before loading (no layout shift). */
    private fun imgAttributes(shot: Screenshot) =
        "alt=\"${escape(shot.caption)}\" width=\"${shot.width}\" height=\"${shot.height}\" loading=\"lazy\" decoding=\"async\""

    /** Where Compose resources land in the Wasm distribution, relative to index.html. */
    fun resourceUrl(path: String) = "$RESOURCES_ROOT/$path"

    private const val RESOURCES_ROOT = "composeResources/dev.yowkey.portfolio.resources"

    /** What a build id may contain: a git hash, a timestamp or a version, nothing that ends a query or a string. */
    private val BUILD_ID = Regex("[A-Za-z0-9._-]+")

    private fun StringBuilder.list(items: List<String>) {
        if (items.isEmpty()) return
        appendLine("<ul>${items.joinToString("") { "<li>${escape(it)}</li>" }}</ul>")
    }

    /** A work article's part, as the detail screen titles it. */
    private fun StringBuilder.titledList(title: String, items: List<String>) {
        if (items.isEmpty()) return
        appendLine("<h5>${escape(title)}</h5>")
        list(items)
    }

    /**
     * With [tracksClicksAt], the backend's address, the links it counts (LinkedIn, GitHub) carry their name as
     * `data-track` and the address as `data-api`, and the page's script sends a beacon when one is opened.
     */
    private fun StringBuilder.links(links: List<Link>, tracksClicksAt: String? = null) {
        if (links.isEmpty()) return
        appendLine("<p class=\"links\">")
        links.forEach { link ->
            val tracked = tracksClicksAt?.let { api ->
                link.trackingKey?.let { " data-track=\"$it\" data-api=\"${escape(api)}\"" }
            }.orEmpty()
            appendLine("<a href=\"${escape(link.url)}\" rel=\"noopener\"$tracked>${escape(link.label)}</a>")
        }
        appendLine("</p>")
    }

    private fun jsonLd(portfolio: Portfolio): String {
        val p = portfolio.profile
        val person = buildJsonObject {
            put("@context", "https://schema.org")
            put("@type", "Person")
            put("name", p.name)
            // The role alone ("Android Developer"), not the whole headline with employer and stack.
            put("jobTitle", p.headline.substringBefore(" at ").substringBefore(" · ").trim())
            put("description", p.summary)
            put("url", portfolio.siteUrl)
            p.email?.let { put("email", "mailto:$it") }
            put("sameAs", JsonArray(portfolio.links.filter { it.url.startsWith("https://") }.map { JsonPrimitive(it.url) }))
        }
        // "</" would end the <script> element early.
        return person.toString().replace("</", "<\\/")
    }

    fun escape(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    private fun StringBuilder.experience(portfolio: Portfolio) {
        if (portfolio.experience.isNotEmpty()) {
            appendLine("<section id=\"experience\"><h2>Experience</h2>")
            portfolio.experience.forEach { job ->
                appendLine("<article><h3>${escape(job.role)} · ${escape(job.company)}</h3>")
                val meta = listOfNotNull(job.period, job.location).joinToString(" · ") { escape(it) }
                appendLine("<p class=\"muted\">$meta</p>")
                list(job.highlights)
                appendLine("</article>")
            }
            appendLine("</section>")
        }
    }

    private fun StringBuilder.recognition(portfolio: Portfolio) {
        if (portfolio.recognition.isNotEmpty()) {
            appendLine("<section id=\"recognition\"><h2>Recognition</h2>")
            portfolio.recognition.forEach { award ->
                appendLine("<article><h3>${escape(award.title)}</h3>")
                award.date?.let { appendLine("<p class=\"muted\">${escape(it)}</p>") }
                appendLine("<p>${escape(award.detail)}</p></article>")
            }
            appendLine("</section>")
        }
    }
}
