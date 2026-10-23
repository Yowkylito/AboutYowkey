package dev.yowkey.portfolio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.yowkey.portfolio.model.Experience
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.model.Service
import dev.yowkey.portfolio.model.SkillGroup
import dev.yowkey.portfolio.ui.navigation.Route
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
        Project(
            "w",
            "A work product",
            "Built at a job.",
            company = "Acme",
            role = "Android developer",
            figures = listOf(ProofPoint("53%", "of the code, written by me")),
            impact = listOf("It shipped."),
        ),
        Project("a", "Short", "One line.", stack = listOf("Kotlin"), visibility = RepoVisibility.Private),
        Project(
            id = "b",
            title = "A project title long enough that it has to wrap onto a second line and then get cut off",
            summary = "A summary that runs long, ".repeat(8),
            highlights = listOf("A highlight that also runs quite long and wraps across lines"),
            stack = List(9) { "Library $it" },
            links = listOf(Link("Source", "https://github.com")),
            period = "Jan – Mar 2026",
            tagline = "A tagline at the limit of ninety characters, to check it wraps on the card cleanly ok",
            visibility = RepoVisibility.Public,
            // No loader in previews, so these show as their sized placeholders.
            cover = Screenshot("splash.webp", "A phone splash screen", 1080, 2400),
            clips = listOf(Screenshot("ride.webp", "A short clip", 720, 405)),
            screenshots = listOf(
                Screenshot("phone.webp", "A phone screen", 1080, 2400),
                Screenshot("desktop.webp", "A desktop screen", 1920, 1080),
            ),
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

@Composable
private fun PreviewScreen(state: PortfolioUiState, route: Route = Route.Home) = PortfolioScreen(
    state = state,
    route = route,
    platformLabel = "Preview",
    onRetry = {},
    onOpenProject = {},
    onBack = {},
    onUnknownProject = {},
    onContentShown = {},
)

@Preview
@Composable
private fun ReadyPreview() = PortfolioTheme {
    PreviewScreen(PortfolioUiState.Ready(previewPortfolio))
}

@Preview
@Composable
private fun ReadyDarkPreview() = PortfolioTheme(darkTheme = true) {
    PreviewScreen(PortfolioUiState.Ready(previewPortfolio))
}

/** Only a profile: every empty section disappears, nav included. */
@Preview
@Composable
private fun EmptySectionsPreview() = PortfolioTheme {
    PreviewScreen(PortfolioUiState.Ready(emptyPortfolio))
}

@Preview
@Composable
private fun LoadingPreview() = PortfolioTheme {
    PreviewScreen(PortfolioUiState.Loading)
}

@Preview
@Composable
private fun ErrorPreview() = PortfolioTheme {
    PreviewScreen(PortfolioUiState.Error("portfolio.json is not valid: ..."))
}

/** A deep link straight to a project: the detail screen, with the long project's media and text. */
@Preview(widthDp = 412, heightDp = 1800)
@Composable
private fun DeepLinkedDetailPreview() = PortfolioTheme {
    PreviewScreen(PortfolioUiState.Ready(previewPortfolio), Route.Project("b"))
}

@Preview
@Composable
private fun LongProjectCardPreview() = PortfolioTheme {
    ProjectCard(previewPortfolio.projects.last(), playing = { false }, onOpen = {})
}
