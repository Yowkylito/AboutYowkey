package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.model.Engineering
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.LiveStats
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
import dev.yowkey.portfolio.ui.components.whereForms
import dev.yowkey.portfolio.ui.components.contactWhere
import dev.yowkey.portfolio.ui.sections.CONTACT_LINE
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.ui.sections.HeroMotion
import dev.yowkey.portfolio.ui.sections.HeroStage
import dev.yowkey.portfolio.ui.sections.SystemFontWidth
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

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
            "heroMotion" to heroMotion(profile),
            "heroStage" to heroStage(profile),
            "minRatings" to LiveStats.MIN_RATINGS.toString(),
            "minVisits" to LiveStats.MIN_VISITS.toString(),
            "build" to build,
        )
        val placeholder = Regex("\\{\\{(\\w+)}}")
        val unknown = placeholder.findAll(template).map { it.groupValues[1] }.toSet() - values.keys - "csp"
        require(unknown.isEmpty()) { "index.template.html uses unknown placeholders: $unknown" }
        // One pass over the template: a value that contains "{{content}}" stays text instead of being filled in turn.
        fun fill(csp: String) = placeholder.replace(template) { (values + ("csp" to csp)).getValue(it.groupValues[1]) }
        // The policy names the page's own inline scripts by hash, so they are read from the page as it will ship.
        val scripts = INLINE_SCRIPT.findAll(fill("")).map { it.groupValues[1] }.toList()
        return fill(escape(contentSecurityPolicy(portfolio, scripts)))
    }

    /**
     * The page's `{{csp}}`: scripts only from the site itself and the page's own inline ones, by hash (so an injected
     * one runs nowhere), Wasm compiled for the app, requests and form posts only to the site and its backend
     * (`api`), no plugins, no `<base>`. Styles stay inline, as the page and the app write them. `frame-ancestors`
     * cannot be set from a `<meta>`; GitHub Pages sends no headers to set it with.
     */
    fun contentSecurityPolicy(portfolio: Portfolio, inlineScripts: List<String>): String {
        val api = portfolio.api?.let { Regex("^https?://[^/]+").find(it)?.value }
        val scripts = inlineScripts.distinct().joinToString("") { " ${Sha256.cspSource(it)}" }
        return listOf(
            "default-src 'self'",
            "script-src 'self' 'wasm-unsafe-eval'$scripts",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "media-src 'self' blob:",
            "font-src 'self' data:",
            "connect-src 'self'" + (api?.let { " $it" } ?: ""),
            "form-action " + (api ?: "'none'"),
            "object-src 'none'",
            "base-uri 'self'",
        ).joinToString("; ")
    }

    /** An inline script the browser runs: `<script>` with no attributes (the JSON-LD block has a type, and only data). */
    private val INLINE_SCRIPT = Regex("<script>([\\s\\S]*?)</script>")

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
        // Where the owner is, and the chip beside it: one cell, so the two stand together and the name replaces both.
        val front = buildString {
            where(p)
            portfolio.api?.let { proofChip(it) }
        }
        if (front.isNotEmpty()) append("<span class=\"front\">$front</span>")
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
        Section.Projects.takeIf { portfolio.projects.isNotEmpty() },
        Section.Skills.takeIf { portfolio.skills.isNotEmpty() },
        Section.Services.takeIf { portfolio.services.isNotEmpty() },
        Section.Recognition.takeIf { portfolio.recognition.isNotEmpty() },
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
    /**
     * Where the owner is and whether they would move, as the app's [WherePill]: the whole line, or on a phone's bar
     * the next shorter of [whereForms]; it holds the slot until the chip has numbers big enough to show, and needs
     * no script.
     */
    private fun StringBuilder.where(profile: Profile) {
        val forms = whereForms(profile)
        if (forms.isEmpty()) return
        val short = forms.getOrElse(1) { forms[0] }
        append("<span class=\"where\" aria-label=\"${escape(forms[0])}\">")
        append("<span class=\"full\" aria-hidden=\"true\">${escape(forms[0])}</span>")
        append("<span class=\"short\" aria-hidden=\"true\">${escape(short)}</span></span>")
    }

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

    /** The hero portrait's trail during its slide, as the app's: three echoes, farthest first. */
    private const val PORTRAIT_ECHOES = HeroMotion.ECHOES

    /**
     * The hero's entrance on the static page, filling `{{heroMotion}}` in the template's styles: [HeroMotion]'s
     * timeline written as CSS, so the static page plays the app's own choreography and the app can take over from it
     * mid-flight without a jump. The figure slides in on the same ease-out (`cubic-bezier`, exactly the app's
     * [HeroMotion.SLIDE_EASE]); each echo follows its path a lag behind the one in front, and the three fade out
     * together on the app's smoothstep, sampled into a `linear()` easing, before the figure settles; then the intro's
     * steps read in, in reading order. `.hero` runs one more animation, `hero-intro`, for the entrance's whole length:
     * it changes nothing and is the clock the app reads (see `staticIntroMillis`).
     *
     * Only where motion is welcome and the page is scripted (the head's `js` class): otherwise the static page shows
     * the still picture and every word at once, and every animation ends there, whatever loads or fails.
     *
     * Without a portrait there is nothing to wait for: the clock starts at [HeroMotion.TEXT_AT_MS], as the app's does
     * then, so the name reads in from the first paint instead of after a blank 420 ms (a first impression forms in
     * 50 to 500 ms, Lindgaard et al. 2006), and the app still joins the same clock.
     */
    fun heroMotion(profile: Profile? = null): String = buildString {
        val m = HeroMotion
        val skipped = if (profile != null && profile.photoPath == null) m.TEXT_AT_MS else 0
        val slide = "${m.SLIDE_MS}ms $SLIDE_EASE"
        val fade = "${m.TRAIL_FADE_MS}ms ${linearCurve(m.TRAIL_FADE_MS) { 1f - m.trail(m.TRAIL_FADE_AT_MS + it) }}"
        appendLine("@media (prefers-reduced-motion: no-preference) {")
        appendLine("  .js .hero { animation: hero-intro ${m.END_MS}ms linear${if (skipped > 0) " -${skipped}ms" else ""} both; }")
        appendLine("  .js .hero .portrait { container-type: inline-size; }")
        appendLine("  .js .hero .portrait .person { animation: hero-dash $slide both; }")
        appendLine(
            "  .js .hero .portrait .echo { animation: hero-dash $slide both, hero-trail $fade both; " +
                "animation-delay: calc(var(--k) * ${m.ECHO_LAG_MS}ms), ${m.TRAIL_FADE_AT_MS}ms; }",
        )
        appendLine("  ${STAGED_STEPS.joinToString(", ") { ".js .hero > $it" }} { animation: hero-step ${m.TEXT_MS}ms $EASE_OUT_CUBIC both; }")
        PLAIN_STEPS.forEachIndexed { i, step ->
            appendLine("  .js .hero > $step { animation-delay: ${m.textAt(i) - skipped}ms; }")
        }
        appendLine("}")
        appendLine("@keyframes hero-intro { from { --hero-intro: 0; } to { --hero-intro: 1; } }")
        // From just past the stage's start edge: the portrait's width (its container) less the figure's inset.
        appendLine("@keyframes hero-dash { from { translate: calc(${decimal(HeroStage.FIGURE_INSET_DP, 1)}px - 100cqw) 0; } }")
        // An echo shows at its own strength (--a) until the trail fades; at rest it is gone (the template's opacity 0).
        appendLine("@keyframes hero-trail { from { opacity: var(--a); } to { opacity: 0; } }")
        appendLine("@keyframes hero-step { from { opacity: 0; translate: -${decimal(m.TEXT_SHIFT_DP, 1)}px 0; } }")
    }

    /**
     * Where the hero's name stands on the static page, filling `{{heroStage}}` in the template's styles: on a single
     * column, on the portrait's stage with the role under it wherever [HeroStage]'s rule lets them, as in the app. CSS
     * cannot measure text, so the rule is worked out here for every width the hero can have (it is the `hero`
     * container), with [SystemFontWidth]'s generous widths; the app takes at least those widths too, so the two
     * decide alike at every width and the hand-off moves nothing. Each width range that stands them on the stage
     * places the name (at the plan's size) and the role (cut to the plan's form), hides what of the headline the stage
     * already says, and, where motion is welcome, reads the role in as a step of its own after the name. Nothing
     * without a portrait.
     */
    fun heroStage(profile: Profile): String = buildString {
        if (profile.photoPath == null) return@buildString
        val role = HeroStage.roleOf(profile.headline)
        val forms = shorterForms(role)
        fun plans(widths: IntRange, height: Float) = widths
            .map { it to HeroStage.plan(profile.name, role, min(it, STAGE_MAX).toFloat(), height, 1f, SystemFontWidth) }
            .fold(mutableListOf<Triple<Int, Int, HeroStage.Plan?>>()) { runs, (width, plan) ->
                val last = runs.lastOrNull()
                if (last != null && last.third == plan) runs[runs.lastIndex] = Triple(last.first, width, plan)
                else runs += Triple(width, width, plan)
                runs
            }
        // Phones: the stage is 168px tall under 600px; from 600px to the wide layout's 840px, 208px.
        listOf(Triple("(max-width: 599px)", 200..599, 168f), Triple("(min-width: 600px) and (max-width: 839px)", 400..839, 208f))
            .forEach { (media, widths, height) ->
                val runs = plans(widths, height)
                runs.forEachIndexed { i, (from, to, plan) ->
                    if (plan == null) return@forEachIndexed
                    val range = if (i == runs.lastIndex) "(width >= ${from}px)" else "(${from}px <= width < ${to + 1}px)"
                    appendLine("@media $media { @container hero $range {")
                    stagedLayout(plan, forms, height)
                    appendLine("} }")
                    appendLine("@media $media and (prefers-reduced-motion: no-preference) { @container hero $range {")
                    stagedMotion()
                    appendLine("} }")
                }
            }
    }

    private fun StringBuilder.stagedLayout(plan: HeroStage.Plan, forms: List<String>, height: Float) {
        val s = HeroStage
        val figure = s.FIGURE_INSET_DP + height * s.FIGURE_MAX_WIDTH
        val hidden = (0 until forms.indexOf(plan.role).coerceAtLeast(0)).joinToString(", ") { ".hero .portrait ~ hgroup .role .c$it" }
        appendLine(
            "  .hero .portrait ~ hgroup { position: absolute; left: ${decimal(s.PAD_DP, 1)}px; " +
                "top: ${decimal(height * s.STAGE_TOP + s.V_PAD_DP, 2)}px; " +
                "width: calc(min(${STAGE_MAX}px, 100cqw) - ${decimal(figure + s.GAP_TO_FIGURE_DP + s.PAD_DP, 2)}px); " +
                "height: ${decimal(s.boxHeight(height), 2)}px; margin: 0; display: flex; flex-direction: column; " +
                "justify-content: center; gap: ${decimal(s.GAP_DP, 1)}px; }",
        )
        // Wrapped word by word, as the rule counts the lines: a balanced or "pretty" wrap could take a line more.
        appendLine(
            "  .hero .portrait ~ hgroup h1 { margin: 0; font-size: ${decimal(plan.nameSp, 1)}px; " +
                "line-height: ${decimal(s.NAME_LINE, 2)}; letter-spacing: -.02em; color: var(--on-stage); text-wrap: wrap; }",
        )
        appendLine(
            "  .hero .portrait ~ hgroup .role { display: block; margin: 0; font-size: ${decimal(plan.roleSp, 1)}px; " +
                "line-height: ${decimal(s.ROLE_LINE, 2)}; font-weight: 500; color: var(--on-stage); text-wrap: wrap; }",
        )
        if (hidden.isNotEmpty()) appendLine("  $hidden { display: none; }")
        if (plan.wholeRole) appendLine("  .hero .portrait ~ .headline .lead, .hero .portrait ~ hgroup.whole + .headline { display: none; }")
    }

    private fun StringBuilder.stagedMotion() {
        val m = HeroMotion
        STAGED_STEPS.forEachIndexed { i, step ->
            appendLine("  .js .hero > $step { animation-delay: ${m.textAt(i)}ms; }")
        }
    }

    /** [at] over [millis] as a CSS `linear()` easing, sampled every 20 ms or so: as good as a frame can show. */
    private fun linearCurve(millis: Int, at: (Float) -> Float): String {
        val samples = millis / 20
        return (0..samples).joinToString(", ", "linear(", ")") { i ->
            val share = i.toFloat() / samples
            "${decimal(at(share * millis), 4)} ${decimal(100f * share, 2)}%"
        }
    }

    /** [value] with at most [places] decimals and never an exponent, as CSS wants: "0.5", "12", "-3.25". */
    private fun decimal(value: Float, places: Int): String {
        var scale = 1L
        repeat(places) { scale *= 10 }
        val scaled = (value.toDouble() * scale).roundToLong()
        val whole = abs(scaled) / scale
        val fraction = (abs(scaled) % scale).toString().padStart(places, '0').trimEnd('0')
        val sign = if (scaled < 0) "-" else ""
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
    }

    /** The intro's steps on the static page, in reading order: under the stage, and with the name and role on it. */
    private val PLAIN_STEPS = listOf("hgroup > h1", ".headline", ".proof", ".links", ".links + p")
    private val STAGED_STEPS = listOf("hgroup > h1", "hgroup > .role", ".headline", ".proof", ".links", ".links + p")

    /** The single column's stage is never wider than this, as the app's. */
    private const val STAGE_MAX = 480

    /** The app's EaseOutCubic, for the intro's steps, and [HeroMotion.SLIDE_EASE], for the figure's slide. */
    private const val EASE_OUT_CUBIC = "cubic-bezier(.33, 1, .68, 1)"
    private const val SLIDE_EASE = "cubic-bezier(.22, 1, .36, 1)"

    /** Three lines, as the app's MenuGlyph; its name is the button's label. */
    private const val MENU_ICON = "<svg width=\"20\" height=\"20\" viewBox=\"0 0 20 20\" aria-hidden=\"true\" focusable=\"false\">" +
        "<path d=\"M2 5h16M2 10h16M2 15h16\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"/></svg>"

    private const val UP_ICON = "<svg width=\"20\" height=\"20\" viewBox=\"0 0 20 20\" aria-hidden=\"true\" focusable=\"false\">" +
        "<path d=\"M10 16V4.5M4.5 10L10 4.5l5.5 5.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" " +
        "stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>"

    /**
     * [role] as its shortest form, then each word group the longer forms add in its own span, the last first to go:
     * "Android Developer<span class="c1"> at DigiPlus</span><span class="c0"> since 2024</span>". Hiding `c0` leaves
     * the next shorter form, hiding `c1` too the one after, and without styles it reads whole.
     */
    private fun roleMarkup(role: String): String {
        val forms = shorterForms(role)
        return buildString {
            append(escape(forms.last()))
            for (i in forms.size - 2 downTo 0) {
                append("<span class=\"c$i\">${escape(forms[i].substring(forms[i + 1].length))}</span>")
            }
        }
    }

    /** The semantic body: one `<section>` per part of the page, in the same order as the Compose UI (`Section`). */
    fun content(portfolio: Portfolio): String = buildString {
        val p = portfolio.profile
        appendLine("<header class=\"hero\">")
        // The app's still picture: the figure on its stage. The echoes are the cut-out again, as CSS masks filled with
        // the accent, shown only while the figure slides in (heroMotion).
        p.photoPath?.let { photo ->
            val url = resourceUrl(photo)
            appendLine("<figure class=\"portrait\" style=\"--photo: url('$url')\">")
            repeat(PORTRAIT_ECHOES) { appendLine("<span class=\"echo\"><i></i></span>") }
            appendLine("<span class=\"person\"><img src=\"$url\" alt=\"Portrait of ${escape(p.name)}\" decoding=\"async\"></span>")
            appendLine("</figure>")
        }
        // The name, then (with a portrait) the role under it, which shows only where the CSS stands both on the stage
        // (heroStage); the headline's lead is marked so it can stand aside then, and each part is said once.
        val separator = p.headline.indexOf('·')
        appendLine(if (separator < 0) "<hgroup class=\"whole\">" else "<hgroup>")
        appendLine("<h1>${escape(p.name)}</h1>")
        if (p.photoPath != null) appendLine("<p class=\"role\">${roleMarkup(HeroStage.roleOf(p.headline))}</p>")
        appendLine("</hgroup>")
        if (separator < 0) {
            appendLine("<p class=\"headline\">${escape(p.headline)}</p>")
        } else {
            val rest = p.headline.substring(separator + 1)
            val lead = p.headline.substring(0, separator + 1) + rest.takeWhile { it == ' ' }
            appendLine("<p class=\"headline\"><span class=\"lead\">${escape(lead)}</span>${escape(rest.trimStart())}</p>")
        }
        // The rating and the visit count are the bar's (its proof chip), so the hero does not repeat them.
        if (p.proof.isNotEmpty()) {
            appendLine("<ul class=\"proof\">")
            p.proof.forEach { appendLine("<li><strong>${escape(it.value)}</strong> ${escape(it.label)}</li>") }
            appendLine("</ul>")
        }
        links(
            listOfNotNull(p.email?.let { Link("Email me", "mailto:$it") }, Link("See projects", "#projects")) + portfolio.links,
            tracksClicksAt = portfolio.api,
            quiet = { it in portfolio.links },
        )
        appendLine("<p>${escape(p.summary)}</p>")
        appendLine("</header>")

        experience(portfolio)

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

        recognition(portfolio)

        appendLine("<section id=\"contact\"><h2>Contact</h2>")
        appendLine("<p class=\"pitch\">${escape(CONTACT_LINE)}</p>")
        contactWhere(p)?.let { appendLine("<p class=\"based\">${escape(it)}</p>") }
        // The address as text, and a copy button the page's script shows where the clipboard can be written.
        p.email?.let {
            appendLine("<p class=\"address\"><span>${escape(it)}</span> <button class=\"copy\" type=\"button\" data-copy=\"${escape(it)}\" hidden>Copy email</button></p>")
        }
        links(listOfNotNull(p.email?.let { Link("Email me", "mailto:$it") }) + portfolio.links, tracksClicksAt = portfolio.api)
        // As in the app: the private form first, then everything public (the published notes, the note form).
        portfolio.api?.let {
            messageForm(it)
            visitorNotes(it)
            feedbackForm(it)
        }
        appendLine("</section>")
    }

    /**
     * A form's place in the Contact section, as the app's: its title with who reads what is sent (the badge), one
     * line, and a button that opens the form in a modal `<dialog>`, which follows it. The title keeps the form's old
     * id ([id], `message` or `feedback`), so a link to `#message` or `#feedback` still lands on it, the button just
     * under it. The dialog is labelled by its own copy of the title and holds [dialogIntro], if any, over the form
     * that [form] writes, unchanged, and a close button ([close]) at its top right. The [primary] form's button is
     * filled, the other's outlined, as the app's.
     *
     * The page's script opens it with `showModal()`: Esc closes it, as does a click on the backdrop or the close
     * button, and the focus goes back to the button. The buttons also name the dialog with `commandfor`, so a
     * browser that knows invoker commands opens and closes it even if the script fails. Without any script (no `js`
     * class on the page) nothing could open it, so the template's CSS shows the form in the section instead, under
     * the section's title, and draws neither the button nor the dialog's head; the form posts plainly to the
     * backend, which answers with a page.
     */
    private fun StringBuilder.formEntry(
        id: String,
        title: String,
        audience: String,
        intro: String,
        open: String,
        close: String,
        primary: Boolean,
        dialogIntro: String? = null,
        form: StringBuilder.() -> Unit,
    ) {
        val dialog = "$id-dialog"
        val badge = "<span class=\"badge\">${escape(audience)}</span>"
        appendLine("<div class=\"form-entry\">")
        appendLine("<h3 id=\"$id\">${escape(title)} $badge</h3>")
        appendLine("<p class=\"note\">${escape(intro)}</p>")
        appendLine(
            "<button class=\"open-form${if (primary) "" else " outlined"}\" type=\"button\" aria-haspopup=\"dialog\" aria-controls=\"$dialog\" " +
                "commandfor=\"$dialog\" command=\"show-modal\">${escape(open)}</button>",
        )
        val described = dialogIntro?.let { " aria-describedby=\"$dialog-intro\"" }.orEmpty()
        appendLine("<dialog class=\"form-dialog\" id=\"$dialog\" aria-labelledby=\"$dialog-title\"$described>")
        appendLine(
            "<div class=\"dialog-head\"><h4 id=\"$dialog-title\">${escape(title)} $badge</h4>" +
                "<button class=\"close\" type=\"button\" aria-label=\"${escape(close)}\" commandfor=\"$dialog\" command=\"close\">$CLOSE_ICON</button></div>",
        )
        appendLine("<div class=\"dialog-body\">")
        dialogIntro?.let { appendLine("<p class=\"dialog-intro\" id=\"$dialog-intro\">${escape(it)}</p>") }
        form()
        appendLine("</div>")
        appendLine("</dialog>")
        appendLine("</div>")
    }

    /**
     * The private message form, as the app's: name (optional), how to reach you (required), the topic as four radio
     * buttons (none picked is filed as "Something else"), the message. It posts to the backend at [api] like the note
     * form: through the page's script, or as a plain form the backend answers with a page. The `website` trap is the
     * note form's too. In the section, only its title, its line and its button ([formEntry]).
     */
    private fun StringBuilder.messageForm(api: String) = formEntry(
        id = "message",
        title = MessageCopy.TITLE,
        audience = MessageCopy.PRIVATE,
        intro = MessageCopy.INTRO,
        open = MessageCopy.OPEN,
        close = MessageCopy.CLOSE,
        primary = false,
    ) {
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
        Relation.offered.forEach {
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
     * people never see it, and a filled one is quietly dropped. In the section, only its title, its line and its
     * button ([formEntry]); what happens to a note is said at the top of its dialog, before anyone writes one.
     */
    private fun StringBuilder.feedbackForm(api: String) = formEntry(
        id = "feedback",
        title = FeedbackCopy.TITLE,
        audience = FeedbackCopy.PUBLIC,
        intro = FeedbackCopy.INTRO,
        open = FeedbackCopy.OPEN,
        close = FeedbackCopy.CLOSE,
        primary = false,
        dialogIntro = FeedbackCopy.FORM_INTRO,
    ) {
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
        Relation.offered.forEach {
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
        // The proof a reader can check (the code, an APK) right under what the project is, not after everything.
        links(project.links)
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
    private fun StringBuilder.links(links: List<Link>, tracksClicksAt: String? = null, quiet: (Link) -> Boolean = { false }) {
        if (links.isEmpty()) return
        appendLine("<p class=\"links\">")
        fun anchor(link: Link) {
            val tracked = tracksClicksAt?.let { api ->
                link.trackingKey?.let { " data-track=\"$it\" data-api=\"${escape(api)}\"" }
            }.orEmpty()
            val style = if (quiet(link)) " class=\"quiet\"" else ""
            appendLine("<a href=\"${escape(link.url)}\" rel=\"noopener\"$style$tracked>${escape(link.label)}</a>")
        }
        // The quiet links stay together (as the app's row), so a narrow screen wraps them as one.
        val (loud, quieter) = links.partition { !quiet(it) }
        loud.forEach(::anchor)
        if (quieter.isNotEmpty()) {
            appendLine("<span class=\"pair\">")
            quieter.forEach(::anchor)
            appendLine("</span>")
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
        // No "<" at all inside the <script>: "</" would end it early, and "<!--<script" would keep it from ending.
        // The JSON escape < reads back as the same character.
        return person.toString().replace("<", "\\u003c")
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
            appendLine("<section id=\"recognition\"><h2>${Section.Recognition.title}</h2>")
            portfolio.recognition.forEach { award ->
                appendLine("<article><h3>${escape(award.title)}</h3>")
                award.date?.let { appendLine("<p class=\"muted\">${escape(it)}</p>") }
                appendLine("<p>${escape(award.detail)}</p></article>")
            }
            appendLine("</section>")
        }
    }
}
