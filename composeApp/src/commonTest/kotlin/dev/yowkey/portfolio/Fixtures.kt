package dev.yowkey.portfolio

import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.Project

val validJson = """
    {
      "siteUrl": "https://example.github.io/site/",
      "profile": { "name": "Ada", "headline": "Engineer", "summary": "Builds things." },
      "projects": [
        { "id": "one", "title": "First <project>", "summary": "S", "links": [{ "label": "Repo", "url": "https://github.com/a/b" }] },
        { "id": "two", "title": "Second", "summary": "S" }
      ],
      "links": [{ "label": "Mail", "url": "mailto:ada@example.com" }],
      "someFutureField": true
    }
""".trimIndent()

fun samplePortfolio(projects: List<Project> = listOf(Project(id = "p", title = "Project", summary = "Summary"))) =
    Portfolio(
        siteUrl = "https://example.github.io/site/",
        profile = Profile(name = "Ada", headline = "Engineer", summary = "Builds things."),
        projects = projects,
        links = listOf(Link("GitHub", "https://github.com/ada")),
    )
