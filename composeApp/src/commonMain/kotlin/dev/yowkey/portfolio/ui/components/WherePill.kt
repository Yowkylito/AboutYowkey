package dev.yowkey.portfolio.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yowkey.portfolio.model.Profile

/**
 * Where the owner is and whether they would move ("Philippines · Open to relocation"), in the bar's slot while it
 * has no proof big enough to show (see [dev.yowkey.portfolio.model.LiveStats]). Location and work authorisation are
 * the filters recruiters screen on first, and the hero keeps its words for the work. A bar too narrow for the whole
 * line gets the next of [whereForms] that fits. Plain words on a hairline pill, not a button: a tap does nothing, so
 * nothing invites one, and the accent stays for what can be pressed.
 */
@Composable
fun WherePill(profile: Profile, modifier: Modifier = Modifier) {
    val forms = remember(profile.location, profile.availability) { whereForms(profile) }
    if (forms.isEmpty()) return
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val room = with(density) { (maxWidth - PillPadding * 2).roundToPx() }
        val text = remember(forms, room, style, density) {
            forms.firstOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width <= room } ?: forms.last()
        }
        Box(
            Modifier
                .height(PillHeight)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(horizontal = PillPadding)
                .clearAndSetSemantics { contentDescription = forms.first() },
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * The pill's words, longest first: the country (the last part of [Profile.location]) with the availability, then the
 * availability alone, then the country alone. Empty when the profile says neither.
 */
fun whereForms(profile: Profile): List<String> {
    val country = profile.location?.substringAfterLast(',')?.trim()?.takeIf { it.isNotEmpty() }
    val availability = profile.availability?.trim()?.takeIf { it.isNotEmpty() }
    return listOfNotNull(
        if (country != null && availability != null) "$country · $availability" else null,
        availability,
        country,
    ).distinct()
}

/**
 * The Contact section's line on where the owner is: "Based in Quezon City, Philippines · Open to relocation", from
 * [Profile.location] and [Profile.availability]; null when the profile says neither.
 */
fun contactWhere(profile: Profile): String? =
    listOfNotNull(profile.location?.trim()?.takeIf { it.isNotEmpty() }?.let { "Based in $it" }, profile.availability?.trim()?.takeIf { it.isNotEmpty() })
        .joinToString(" · ").ifEmpty { null }

private val PillHeight = 32.dp
private val PillPadding = 12.dp
