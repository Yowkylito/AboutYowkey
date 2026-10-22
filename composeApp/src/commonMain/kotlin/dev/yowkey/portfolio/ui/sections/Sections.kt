package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Experience
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.Service
import dev.yowkey.portfolio.model.SkillGroup
import dev.yowkey.portfolio.ui.components.Bullet
import dev.yowkey.portfolio.ui.components.LinkButton
import dev.yowkey.portfolio.ui.components.OutlinedCard
import dev.yowkey.portfolio.ui.components.Tag

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Hero(
    profile: Profile,
    platformLabel: String,
    compact: Boolean,
    onSeeProjects: () -> Unit,
    onContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        profile.availability?.let {
            Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = profile.name,
            style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = profile.headline,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        Text(
            text = profile.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 640.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSeeProjects) { Text("See projects") }
            OutlinedButton(onClick = onContact) { Text("Get in touch") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profile.location?.let { Tag(it) }
            Tag("Rendered by $platformLabel")
        }
    }
}

/** Lays out one row of an equal-height card grid; short rows keep their column width. */
@Composable
fun CardRow(
    columns: Int,
    count: Int,
    modifier: Modifier = Modifier,
    card: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) { card(it, Modifier.weight(1f).fillMaxHeight()) }
        repeat(columns - count) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
fun ServiceCard(service: Service, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(service.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(service.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectCard(project: Project, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    project.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                project.role?.let {
                    Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(project.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (project.highlights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { project.highlights.forEach { Bullet(it) } }
            }
            Spacer(Modifier.weight(1f))
            if (project.stack.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    project.stack.forEach { Tag(it) }
                }
            }
            if (project.links.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { project.links.forEach { LinkButton(it) } }
            }
        }
    }
}

/** A timeline entry; the rail is drawn, not composed, and stops at the last job. */
@Composable
fun JobEntry(experience: Experience, isLast: Boolean, wide: Boolean, modifier: Modifier = Modifier) {
    val rail = MaterialTheme.colorScheme.outline
    val dot = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth()) {
        if (wide) {
            Text(
                experience.period,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(150.dp).padding(top = 2.dp, end = 16.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .drawBehind {
                    val x = 4.dp.toPx()
                    if (!isLast) drawLine(rail, Offset(x, 16.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                    drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x, 9.dp.toPx()))
                }
                .padding(start = 28.dp, bottom = if (isLast) 0.dp else 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${experience.role} · ${experience.company}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val meta = listOfNotNull(experience.period.takeUnless { wide }, experience.location).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (experience.highlights.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    experience.highlights.forEach { Bullet(it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillGroups(groups: List<SkillGroup>, columns: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        groups.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { group ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.items.forEach { Tag(it) }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactBlock(email: String?, links: List<Link>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            "Have a project in mind? Let's build it.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        email?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            email?.let { LinkButton(Link("Email me", "mailto:$it"), primary = true) }
            links.forEachIndexed { index, link -> LinkButton(link, primary = email == null && index == 0) }
        }
    }
}

@Composable
fun Footer(platformLabel: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.padding(bottom = 8.dp)) {
            Text(
                "Built with Compose Multiplatform · running on $platformLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
