package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.ui.components.LinkButton
import dev.yowkey.portfolio.ui.components.OutlinedCard
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import dev.yowkey.portfolio.ui.theme.PortfolioTheme

/**
 * The first screen, written for a seven-second skim: who (name), what and where now (headline), why (proof), and
 * how to reach out, all above the fold. The summary comes after the buttons so it can never push them down. The
 * profile's location and availability are not shown here.
 *
 * [wide] puts the proof in a card beside the intro, under the portrait ([Profile.photo], see [HeroPortrait]);
 * otherwise the portrait leads and the proof sits between headline and buttons. The primary button mails
 * [Profile.email] directly; without an email it falls back to [onContact] (the Contact section). The notes' rating
 * and the visit count are not repeated here: the bar shows them while this is on screen.
 *
 * It comes in through [entrance], once per page load: the portrait dashes in and lands, then the intro reads in, in
 * reading order whichever the layout (name, headline, proof, buttons, summary), each step fading in as it slides
 * toward the end. Every step keeps its space while it waits, so the hero's size never changes and nothing below it
 * (nor the bar, which measures it) moves.
 */
@Composable
fun Hero(
    profile: Profile,
    compact: Boolean,
    wide: Boolean,
    onSeeProjects: () -> Unit,
    onContact: () -> Unit,
    modifier: Modifier = Modifier,
    entrance: HeroEntrance = rememberHeroEntrance(),
) {
    val photo = profile.photoPath
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(entrance, reducedMotion) { entrance.play(reducedMotion, hasPhoto = photo != null) }
    if (wide && profile.proof.isNotEmpty()) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HeroIntro(profile, compact, entrance)
                HeroActions(profile.email, onSeeProjects, onContact, Modifier.introStep(entrance, IntroStep.ACTIONS))
                HeroSummary(profile.summary, Modifier.introStep(entrance, IntroStep.SUMMARY))
            }
            Spacer(Modifier.width(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                photo?.let { HeroPortrait(it, profile.name, entrance, Modifier.fillMaxWidth().height(224.dp)) }
                OutlinedCard(Modifier.fillMaxWidth().introStep(entrance, IntroStep.PROOF)) {
                    ProofPoints(profile.proof, Modifier.padding(28.dp))
                }
            }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            photo?.let {
                HeroPortrait(
                    it, profile.name, entrance,
                    Modifier.widthIn(max = 480.dp).fillMaxWidth().height(if (compact) 168.dp else 208.dp),
                )
            }
            HeroIntro(profile, compact, entrance)
            if (profile.proof.isNotEmpty()) {
                ProofPoints(profile.proof, Modifier.padding(vertical = 4.dp).introStep(entrance, IntroStep.PROOF))
            }
            HeroActions(profile.email, onSeeProjects, onContact, Modifier.introStep(entrance, IntroStep.ACTIONS))
            HeroSummary(profile.summary, Modifier.introStep(entrance, IntroStep.SUMMARY))
        }
    }
}

@Composable
private fun HeroIntro(profile: Profile, compact: Boolean, entrance: HeroEntrance) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = profile.name,
            style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }.introStep(entrance, IntroStep.NAME),
        )
        Text(
            text = profile.headline,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.readingMeasure(MaterialTheme.typography.headlineSmall).introStep(entrance, IntroStep.HEADLINE),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroActions(email: String?, onSeeProjects: () -> Unit, onContact: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (email != null) {
            LinkButton(Link("Email me", "mailto:$email"), primary = true)
        } else {
            Button(onClick = onContact) { Text("Get in touch") }
        }
        OutlinedButton(onClick = onSeeProjects) { Text("See projects") }
    }
}

@Composable
private fun HeroSummary(summary: String, modifier: Modifier = Modifier) {
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.readingMeasure(MaterialTheme.typography.bodyLarge),
    )
}

/** Each point is one merged node, so a screen reader says the figure and its sentence together. */
@Composable
fun ProofPoints(points: List<ProofPoint>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        points.forEach { point ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(point.value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(point.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val previewProfile = Profile(
    name = "Ada Lovelace",
    headline = "Android Developer at Analytical Engines · Kotlin, Jetpack Compose",
    summary = "Ships Compose apps end to end.",
    location = "London",
    email = "ada@example.com",
    availability = "Open to relocation",
    photo = "portrait.webp",
    proof = listOf(
        ProofPoint("53%", "of the app's code written by me, from day one"),
        ProofPoint("#1 of ~40", "engineers by story points, at 2.4x the team average"),
        ProofPoint("Rank #2", "Top 9 Awardee, picked by leadership"),
    ),
)

@Preview(widthDp = 412)
@Composable
private fun CompactHeroPreview() = PortfolioTheme {
    Hero(previewProfile, compact = true, wide = false, onSeeProjects = {}, onContact = {})
}

@Preview(widthDp = 1120)
@Composable
private fun WideHeroPreview() = PortfolioTheme {
    Hero(previewProfile, compact = false, wide = true, onSeeProjects = {}, onContact = {})
}

/** No proof, email or photo: the wide layout falls back to one column and the button to the Contact section. */
@Preview(widthDp = 1120)
@Composable
private fun BareHeroPreview() = PortfolioTheme {
    Hero(
        previewProfile.copy(proof = emptyList(), email = null, photo = null),
        compact = false, wide = true, onSeeProjects = {}, onContact = {},
    )
}
