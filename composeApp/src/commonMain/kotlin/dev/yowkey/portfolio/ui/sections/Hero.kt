package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Profile
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.ui.components.LinkButton
import dev.yowkey.portfolio.ui.components.OutlinedCard
import kotlin.math.max
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
 * On a single column (compact and medium) the name and the role, the headline's lead, stand on the portrait's stage
 * at its start side when they fit there ([HeroStage], decided by measuring before the first frame), and the line
 * under the stage says only the rest of the headline; when they do not fit, the name and the whole headline are under
 * the stage. The wide layout keeps the name in the column beside the portrait.
 *
 * It comes in through [entrance], once per page load: the portrait dashes in and lands, then the intro reads in, in
 * reading order whichever the layout (name, role when staged, headline, proof, buttons, summary), each step fading in
 * as it slides toward the end; staged, the trail first draws back to clear the stage for the name. Every step keeps
 * its space while it waits, so the hero's size never changes and nothing below it (nor the bar, which measures it)
 * moves.
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
        BoxWithConstraints(modifier) {
            val stageHeight = if (compact) 168.dp else 208.dp
            val stageWidth = min(maxWidth, StageMaxWidth)
            val plan = photo?.let { rememberStagePlan(profile.name, HeroStage.roleOf(profile.headline), stageWidth, stageHeight) }
            val staged = plan != null
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                photo?.let {
                    Box(Modifier.widthIn(max = StageMaxWidth).fillMaxWidth().height(stageHeight)) {
                        HeroPortrait(it, profile.name, entrance, Modifier.matchParentSize(), staged = staged)
                        plan?.let { StagedTitle(profile.name, it, entrance, stageWidth, stageHeight) }
                    }
                }
                if (plan == null) HeroIntro(profile, compact, entrance) else HeadlineBelow(profile.headline, plan, entrance)
                if (profile.proof.isNotEmpty()) {
                    ProofPoints(profile.proof, Modifier.padding(vertical = 4.dp).introStep(entrance, IntroStep.PROOF, staged))
                }
                HeroActions(profile.email, onSeeProjects, onContact, Modifier.introStep(entrance, IntroStep.ACTIONS, staged))
                HeroSummary(profile.summary, Modifier.introStep(entrance, IntroStep.SUMMARY, staged))
            }
        }
    }
}

/**
 * Whether the name and the role can stand on this stage ([HeroStage.plan]), measured in the app's own font at the
 * current font scale and never narrower than the static page's estimate ([SystemFontWidth]), so both pages decide
 * alike. Kept while the width, the font scale and the words stay the same, so it costs nothing per frame.
 */
@Composable
private fun rememberStagePlan(name: String, role: String, stageWidth: Dp, stageHeight: Dp): HeroStage.Plan? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(name, role, stageWidth, stageHeight, density, measurer) {
        val widths = TextWidth { text, sizeDp, bold ->
            val sizeSp = sizeDp / density.fontScale
            val style = if (bold) stageNameStyle(sizeSp) else stageRoleStyle(sizeSp)
            val measured = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width / density.density
            max(measured, SystemFontWidth.width(text, sizeDp, bold))
        }
        HeroStage.plan(name, role, stageWidth.value, stageHeight.value, density.fontScale, widths)
    }
}

/**
 * The name, the page's heading, and the role under it, standing on the stage's start side in the box [HeroStage]
 * keeps for them, centred in the tinted area. Drawn over the portrait, so the trail (which draws back clear of them)
 * never covers them; in onPrimaryContainer, at least 6:1 on the stage in either theme even where the face's glow
 * reaches.
 */
@Composable
private fun StagedTitle(name: String, plan: HeroStage.Plan, entrance: HeroEntrance, stageWidth: Dp, stageHeight: Dp) {
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        Modifier
            .padding(start = HeroStage.PAD_DP.dp, top = stageHeight * HeroStage.STAGE_TOP + HeroStage.V_PAD_DP.dp)
            .width(HeroStage.boxWidth(stageWidth.value, stageHeight.value).dp)
            .height(HeroStage.boxHeight(stageHeight.value).dp),
        verticalArrangement = Arrangement.spacedBy(HeroStage.GAP_DP.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = name,
            style = stageNameStyle(plan.nameSp),
            color = color,
            modifier = Modifier.semantics { heading() }.introStep(entrance, IntroStep.NAME, staged = true),
        )
        Text(
            text = plan.role,
            style = stageRoleStyle(plan.roleSp),
            color = color,
            modifier = Modifier.introStep(entrance, IntroStep.ROLE, staged = true),
        )
    }
}

/**
 * The headline's line under a staged name: only what the stage did not say (the skills after the role) when the
 * whole role stands there, else the whole headline, so each part of it is on the page once.
 */
@Composable
private fun HeadlineBelow(headline: String, plan: HeroStage.Plan, entrance: HeroEntrance) {
    val text = if (plan.wholeRole) headline.substringAfter('·', "").trim() else headline
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.readingMeasure(MaterialTheme.typography.headlineSmall).introStep(entrance, IntroStep.HEADLINE, staged = true),
    )
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

/**
 * The staged name: bold, set tight, at one of [HeroStage.NAME_SIZES_SP]. Both staged styles wrap word by word, as the
 * rule counts their lines; a balanced wrap could take a line more than the box holds.
 */
private fun stageNameStyle(sizeSp: Float) = TextStyle(
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * HeroStage.NAME_LINE).sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.02).em,
    lineBreak = LineBreak.Simple,
)

private fun stageRoleStyle(sizeSp: Float) = TextStyle(
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * HeroStage.ROLE_LINE).sp,
    fontWeight = FontWeight.Medium,
    lineBreak = LineBreak.Simple,
)

/** The single column's stage is never wider than this. */
private val StageMaxWidth = 480.dp
