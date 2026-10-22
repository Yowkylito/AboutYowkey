package dev.yowkey.portfolio.site

import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Portfolio
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
        appendLine("<p>${escape(p.summary)}</p>")
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
                project.role?.let { appendLine("<p class=\"muted\">${escape(it)}</p>") }
                appendLine("<p>${escape(project.summary)}</p>")
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

    private fun StringBuilder.list(items: List<String>) {
        if (items.isEmpty()) return
        appendLine("<ul>${items.joinToString("") { "<li>${escape(it)}</li>" }}</ul>")
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
