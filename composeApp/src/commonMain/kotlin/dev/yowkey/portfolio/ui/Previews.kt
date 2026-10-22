package dev.yowkey.portfolio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.yowkey.portfolio.model.Experience
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.Service
import dev.yowkey.portfolio.model.SkillGroup
import dev.yowkey.portfolio.ui.sections.ProjectCard
import dev.yowkey.portfolio.ui.theme.PortfolioTheme

private val previewPortfolio = Portfolio(
    siteUrl = "https://example.github.io/site/",
    profile = Profile(
        name = "Ada Lovelace",
        headline = "Android engineer",
        summary = "Ships Compose apps end to end.",
        location = "Remote",
        availability = "Open to work",
    ),
    services = listOf(Service("Android apps", "From first screen to Play Store.")),
    projects = listOf(
        Project("a", "Short", "One line.", stack = listOf("Kotlin")),
        Project(
            id = "b",
            title = "A project title long enough that it has to wrap onto a second line and then get cut off",
            summary = "A summary that runs long, ".repeat(8),
            highlights = listOf("A highlight that also runs quite long and wraps across lines"),
            stack = List(9) { "Library $it" },
            links = listOf(Link("Source", "https://github.com")),
        ),
    ),
    experience = listOf(Experience("Engineer", "Company", "2020 – Present", highlights = listOf("Did things"))),
    skills = listOf(SkillGroup("Android", listOf("Kotlin", "Compose"))),
    links = listOf(Link("GitHub", "https://github.com")),
)

private val emptyPortfolio = previewPortfolio.copy(
    services = emptyList(),
    projects = emptyList(),
    experience = emptyList(),
    skills = emptyList(),
)

@Preview
@Composable
private fun ReadyPreview() = PortfolioTheme {
    PortfolioScreen(PortfolioUiState.Ready(previewPortfolio), "Preview", onRetry = {}, onContentShown = {})
}

@Preview
@Composable
private fun ReadyDarkPreview() = PortfolioTheme(darkTheme = true) {
    PortfolioScreen(PortfolioUiState.Ready(previewPortfolio), "Preview", onRetry = {}, onContentShown = {})
}

/** Only a profile: every empty section disappears, nav included. */
@Preview
@Composable
private fun EmptySectionsPreview() = PortfolioTheme {
    PortfolioScreen(PortfolioUiState.Ready(emptyPortfolio), "Preview", onRetry = {}, onContentShown = {})
}

@Preview
@Composable
private fun LoadingPreview() = PortfolioTheme {
    PortfolioScreen(PortfolioUiState.Loading, "Preview", onRetry = {}, onContentShown = {})
}

@Preview
@Composable
private fun ErrorPreview() = PortfolioTheme {
    PortfolioScreen(PortfolioUiState.Error("portfolio.json is not valid: ..."), "Preview", onRetry = {}, onContentShown = {})
}

@Preview
@Composable
private fun LongProjectCardPreview() = PortfolioTheme {
    ProjectCard(previewPortfolio.projects.last())
}
