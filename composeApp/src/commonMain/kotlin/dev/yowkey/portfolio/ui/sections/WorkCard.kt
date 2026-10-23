package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Engineering
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.ui.components.OutlinedCard
import dev.yowkey.portfolio.ui.components.TechAndPlatforms
import dev.yowkey.portfolio.ui.theme.PortfolioTheme

/**
 * A work project at a glance. The code is the company's, so where a personal card leads with screens, this one
 * leads with what a hiring manager asks of a job: the product, company, role and period, one line, and the numbers
 * that size my part. Same frame, padding, chips and button as [ProjectCard], so the two groups read as one grid, and
 * the button sits at the bottom so a row of two stays level.
 */
@Composable
fun WorkCard(project: Project, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier, onClick = onOpen, onClickLabel = "View ${project.title}") {
        Column(Modifier.fillMaxHeight().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProjectHeading(project, MaterialTheme.typography.titleLarge, maxTitleLines = 2)
            Text(
                project.cardLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (project.figures.isNotEmpty()) FigureList(project.figures, large = false, Modifier.padding(vertical = 4.dp))
            TechAndPlatforms(project.headlineTech, project.engineering?.platforms.orEmpty(), Modifier.padding(top = 2.dp))
            Spacer(Modifier.weight(1f))
            ViewProjectButton(onOpen, Modifier.padding(top = 6.dp))
        }
    }
}

/**
 * Headline numbers, one per row: the figure in a fixed-width column, its label beside it, on a light accent wash.
 * Rows rather than tiles side by side, because three tiles leave a phone card about 70dp a figure, too narrow for
 * "30 days" and its label, while a row fits any width. Each row reads as one phrase to a screen reader. Figure on
 * the wash: 4.7:1 in light, 7.0:1 in dark (and it is large text); label 16.1:1 and 13.4:1.
 */
@Composable
fun FigureList(figures: List<ProofPoint>, large: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val valueStyle = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge
    Column(
        modifier
            .fillMaxWidth()
            .background(scheme.primary.copy(alpha = 0.07f).compositeOver(scheme.surface), RoundedCornerShape(12.dp))
            .padding(horizontal = if (large) 24.dp else 16.dp, vertical = if (large) 20.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (large) 14.dp else 10.dp),
    ) {
        figures.forEach { figure ->
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    figure.value,
                    style = valueStyle,
                    color = scheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(if (large) FigureWidthLarge else FigureWidth).alignByBaseline(),
                )
                Text(
                    figure.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
            }
        }
    }
}

/** Wide enough for the parser's longest figure ([Project.MAX_FIGURE_VALUE] characters, "30 days") at each size. */
private val FigureWidth = 104.dp
private val FigureWidthLarge = 136.dp

private val previewWork = Project(
    id = "arenaplus",
    title = "ArenaPlus",
    summary = "A sports entertainment app.",
    tagline = "Co-built the V2 Android app from day one and led its move from WebView to native",
    company = "DigiPlus",
    role = "Android developer",
    period = "Sep 2024 – Present",
    figures = listOf(ProofPoint("53%", "of V2's code, written by me"), ProofPoint("30 days", "a label long enough to wrap onto a second line")),
    engineering = Engineering(builtWith = listOf("Jetpack Compose", "Koin"), platforms = listOf("Android")),
)

@Preview(widthDp = 360)
@Composable
private fun WorkCardPhonePreview() = PortfolioTheme {
    WorkCard(previewWork, onOpen = {})
}

@Preview(widthDp = 520)
@Composable
private fun WorkCardDarkPreview() = PortfolioTheme(darkTheme = true) {
    WorkCard(previewWork, onOpen = {})
}

/** No figures: the card is its text, chips and button. */
@Preview(widthDp = 420)
@Composable
private fun WorkCardWithoutFiguresPreview() = PortfolioTheme {
    WorkCard(previewWork.copy(figures = emptyList(), engineering = null), onOpen = {})
}
