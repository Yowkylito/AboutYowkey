package dev.yowkey.portfolio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.ui.components.MenuGlyph
import dev.yowkey.portfolio.ui.components.ThemeToggle

/**
 * The sticky navigation bar, with the theme switch at its end. [scrolled] is a lambda so the divider is decided in
 * the draw phase: scrolling never recomposes the bar, only [active] changes do.
 */
@Composable
fun TopBar(
    name: String,
    sections: List<Section>,
    active: Section,
    compact: Boolean,
    gutter: Dp,
    maxWidth: Dp,
    scrolled: () -> Boolean,
    onNavigate: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val divider = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(background)
                if (scrolled()) drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().height(64.dp).padding(horizontal = gutter - 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onNavigate(Section.Home) }) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            Box(Modifier.weight(1f))
            if (compact) {
                ThemeToggle()
                CompactMenu(sections, active, onNavigate)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    sections.forEach { section -> NavLink(section, section == active, onNavigate) }
                }
                ThemeToggle()
            }
        }
    }
}

@Composable
private fun NavLink(section: Section, isActive: Boolean, onNavigate: (Section) -> Unit) {
    TextButton(onClick = { onNavigate(section) }, modifier = Modifier.semantics { selected = isActive }) {
        Text(
            section.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactMenu(sections: List<Section>, active: Section, onNavigate: (Section) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "Open navigation menu" },
        ) { MenuGlyph() }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sections.forEach { section ->
                DropdownMenuItem(
                    text = {
                        Text(
                            section.title,
                            color = if (section == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onNavigate(section)
                    },
                )
            }
        }
    }
}
