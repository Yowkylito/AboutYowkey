package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.text.Emphasis
import dev.yowkey.portfolio.text.EmphasisKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The crawlable HTML twin of the Compose UI. Search engines, link previews and browsers without WasmGC read this;
 * everyone else sees it until the Compose app has loaded.
 */
object StaticHtml {

    /** Fills the `{{placeholders}}` of web/index.template.html. */
    fun page(portfolio: Portfolio, template: String): String {
        val profile = portfolio.profile
        val values = mapOf(
            "title" to escape("${profile.name} — ${profile.headline}"),
            "description" to escape(profile.summary),
            "url" to escape(portfolio.siteUrl),
            "name" to escape(profile.name),
            "jsonLd" to jsonLd(portfolio),
            "content" to content(portfolio),
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

    /** The semantic body: one `<section>` per part of the page, in the same order as the Compose UI. */
    fun content(portfolio: Portfolio): String = buildString {
        val p = portfolio.profile
        appendLine("<header class=\"hero\">")
        p.availability?.let { appendLine("<p class=\"eyebrow\">${escape(it)}</p>") }
        appendLine("<h1>${escape(p.name)}</h1>")
        appendLine("<p class=\"headline\">${escape(p.headline)}</p>")
        appendLine("<p class=\"lead\">${emphasize(p.summary)}</p>")
        p.location?.let { appendLine("<p class=\"muted\">${escape(it)}</p>") }
        appendLine("</header>")

        if (portfolio.services.isNotEmpty()) {
            appendLine("<section id=\"services\"><h2>Services</h2><ul class=\"cards\">")
            portfolio.services.forEach {
                appendLine("<li><h3>${escape(it.title)}</h3><p>${escape(it.description)}</p></li>")
            }
            appendLine("</ul></section>")
        }

        if (portfolio.projects.isNotEmpty()) {
            appendLine("<section id=\"projects\"><h2>Projects</h2>")
            portfolio.projects.forEach { project ->
                appendLine("<article><h3>${escape(project.title)}</h3>")
                val meta = listOfNotNull(project.role, project.period)
                if (meta.isNotEmpty()) appendLine("<p class=\"muted\">${meta.joinToString(" · ") { escape(it) }}</p>")
                screenshots(project.screenshots)
                appendLine("<p>${emphasize(project.summary)}</p>")
                list(project.highlights)
                if (project.stack.isNotEmpty()) {
                    appendLine("<p class=\"muted\">${project.stack.joinToString(" · ") { escape(it) }}</p>")
                }
                links(project.links)
                appendLine("</article>")
            }
            appendLine("</section>")
        }

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

        if (portfolio.recognition.isNotEmpty()) {
            appendLine("<section id=\"recognition\"><h2>Recognition</h2>")
            portfolio.recognition.forEach { award ->
                appendLine("<article><h3>${escape(award.title)}</h3>")
                award.date?.let { appendLine("<p class=\"muted\">${escape(it)}</p>") }
                appendLine("<p>${emphasize(award.detail)}</p></article>")
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

        appendLine("<section id=\"contact\"><h2>Contact</h2>")
        links(listOfNotNull(p.email?.let { Link("Email", "mailto:$it") }) + portfolio.links)
        appendLine("</section>")
    }

    /** Thumbnails linking to the full image; width/height let the browser reserve the space (no layout shift). */
    private fun StringBuilder.screenshots(shots: List<Screenshot>) {
        if (shots.isEmpty()) return
        appendLine("<div class=\"shots\">")
        shots.forEach { shot ->
            appendLine(
                "<a href=\"${resourceUrl(shot.fullPath)}\"><img src=\"${resourceUrl(shot.thumbPath)}\" " +
                    "alt=\"${escape(shot.caption)}\" width=\"${shot.width}\" height=\"${shot.height}\" " +
                    "loading=\"lazy\" decoding=\"async\"></a>",
            )
        }
        appendLine("</div>")
    }

    /** Where Compose resources land in the Wasm distribution, relative to index.html. */
    fun resourceUrl(path: String) = "$RESOURCES_ROOT/$path"

    private const val RESOURCES_ROOT = "composeResources/dev.yowkey.portfolio.resources"

    private fun StringBuilder.list(items: List<String>) {
        if (items.isEmpty()) return
        appendLine("<ul>${items.joinToString("") { "<li>${emphasize(it)}</li>" }}</ul>")
    }

    private fun StringBuilder.links(links: List<Link>) {
        if (links.isEmpty()) return
        appendLine("<p class=\"links\">")
        links.forEach {
            appendLine("<a href=\"${escape(it.url)}\" rel=\"noopener\">${escape(it.label)}</a>")
        }
        appendLine("</p>")
    }

    private fun jsonLd(portfolio: Portfolio): String {
        val p = portfolio.profile
        val person = buildJsonObject {
            put("@context", "https://schema.org")
            put("@type", "Person")
            put("name", p.name)
            put("jobTitle", p.headline)
            put("description", p.summary)
            put("url", portfolio.siteUrl)
            p.email?.let { put("email", "mailto:$it") }
            put("sameAs", JsonArray(portfolio.links.filter { it.url.startsWith("https://") }.map { JsonPrimitive(it.url) }))
        }
        // "</" would end the <script> element early.
        return person.toString().replace("</", "<\\/")
    }

    /**
     * [text] escaped, with its numeric facts in `<strong>`, as the app marks them: wins (53%, 2.4x, #1) also get
     * `class="win"` for the accent colour.
     */
    fun emphasize(text: String): String = Emphasis.segments(text).joinToString("") { (run, kind) ->
        when (kind) {
            null -> escape(run)
            EmphasisKind.Strong -> "<strong class=\"win\">${escape(run)}</strong>"
            EmphasisKind.Figure -> "<strong>${escape(run)}</strong>"
        }
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
}
