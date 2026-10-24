package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Engineering
import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.ui.components.ArrowGlyph
import dev.yowkey.portfolio.ui.components.OutlinedCard
import dev.yowkey.portfolio.ui.components.PlayGlyph
import dev.yowkey.portfolio.ui.components.StatusBadge
import dev.yowkey.portfolio.ui.components.TechAndPlatforms
import dev.yowkey.portfolio.ui.components.WrapRow
import dev.yowkey.portfolio.ui.image.CoverClip
import dev.yowkey.portfolio.ui.image.LocalScreenshotImages
import dev.yowkey.portfolio.ui.image.ScreenshotImage
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import kotlinx.coroutines.CancellationException

/**
 * A project at a glance, for the recruiter's three questions: what is it (cover, title, one line), what was it
 * built with (tech and platform chips), and does it look good (the cover, playing when it is a clip). Everything
 * else (highlights, the full stack, the gallery, links) is one tap away on the detail screen, so cards stay short
 * and a row of two stays level: the button sits at the bottom whatever the text above it.
 *
 * @param playing whether this card's cover clip may play now. A lambda read inside the cover, so the page's
 *   choice of playing covers (which changes while scrolling) recomposes only the covers whose answer changed.
 * @param onOpen opens the detail screen; the whole card and its button do the same.
 */
@Composable
fun ProjectCard(
    project: Project,
    playing: () -> Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier, onClick = onOpen, onClickLabel = "View ${project.title}") {
        Column(Modifier.fillMaxHeight()) {
            CoverStage(project, playing)
            Column(
                Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProjectHeading(project, MaterialTheme.typography.titleLarge, maxTitleLines = 2)
                Text(
                    project.cardLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                TechAndPlatforms(project.headlineTech, project.engineering?.platforms.orEmpty(), Modifier.padding(top = 2.dp))
                Spacer(Modifier.weight(1f))
                ViewProjectButton(onOpen, Modifier.padding(top = 6.dp))
            }
        }
    }
}

/**
 * The app's icon and title, then company · role · period, the repository badge and, for an unofficial concept,
 * its badge. Shared by the cards and the detail screen.
 *
 * @param iconSize the icon's side, sized to the title beside it
 * @param showCompany false on a work card, whose group header already names the company
 */
@Composable
fun ProjectHeading(
    project: Project,
    titleStyle: TextStyle,
    modifier: Modifier = Modifier,
    maxTitleLines: Int = Int.MAX_VALUE,
    iconSize: Dp = 40.dp,
    showCompany: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            project.iconPath?.let { path ->
                AppIcon(path, Modifier.size(iconSize))
                Spacer(Modifier.width(iconSize * 0.3f))
            }
            Text(
                project.title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxTitleLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val meta = if (showCompany) project.meta else project.roleAndPeriod
        if (meta.isNotEmpty() || project.visibility != null || project.disclaimer != null) {
            WrapRow(horizontalSpacing = 10.dp, verticalSpacing = 6.dp) {
                if (meta.isNotEmpty()) {
                    Text(meta.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                project.visibility?.let { StatusBadge(it.badge) }
                if (project.disclaimer != null) StatusBadge(Project.UNOFFICIAL_BADGE)
            }
        }
    }
}

/**
 * A launcher icon, rounded as a home screen rounds it, with a hairline so a white or near-black icon keeps its edge
 * on either theme. Decorative: the title beside it names the app. The tile stays empty until the icon loads, unless
 * the cache already holds it (a card scrolled back into view), when it is there from the first frame.
 */
@Composable
private fun AppIcon(path: String, modifier: Modifier = Modifier) {
    val loader = LocalScreenshotImages.current?.thumbnails
    val cached = remember(path, loader) { loader?.peek(path) }
    val icon by produceState(cached, path, loader) {
        value = cached ?: try {
            loader?.get(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // the tests guard that every listed icon ships
        }
    }
    val shape = RoundedCornerShape(percent = 22)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        icon?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
fun ViewProjectButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text("View project")
        Spacer(Modifier.width(8.dp))
        ArrowGlyph(MaterialTheme.colorScheme.onPrimary)
    }
}

/**
 * The card's top: a fixed-height stage on a soft accent wash, so every card in a row starts its text at the same
 * height whatever its media. A portrait cover sits in a phone frame, a landscape one inset with rounded corners,
 * and a project with no media shows its initials. All sizes come from the JSON, so nothing moves on load.
 */
@Composable
fun CoverStage(project: Project, playing: () -> Boolean, modifier: Modifier = Modifier, height: Dp = CardStageHeight) {
    val media = remember(project) { project.leadMedia }
    val scheme = MaterialTheme.colorScheme
    val wash = scheme.primary.copy(alpha = 0.10f).compositeOver(scheme.surfaceVariant)
    val glow = scheme.primary.copy(alpha = 0.16f)
    val base = scheme.surfaceVariant
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val gradient = Brush.verticalGradient(listOf(wash, base))
                val halo = Brush.radialGradient(
                    listOf(glow, glow.copy(alpha = 0f)),
                    center = Offset(size.width / 2, size.height * 0.35f),
                    radius = size.maxDimension * 0.55f,
                )
                onDrawBehind {
                    drawRect(gradient)
                    drawRect(halo)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (media == null) {
            Monogram(project.title)
        } else {
            val isPlaying by rememberUpdatedState(playing)
            val play by remember { derivedStateOf { isPlaying() } }
            MediaFrame(media.shot, Modifier.padding(vertical = 22.dp, horizontal = 20.dp)) { frame ->
                when (media.kind) {
                    GalleryItem.Kind.CoverClip ->
                        CoverClip(media.shot, playing = play, modifier = frame, cornerRadius = mediaFrameCorner(media.shot))
                    else -> Box(frame, contentAlignment = Alignment.Center) {
                        ScreenshotImage(media.cardPath, media.shot.caption, LocalScreenshotImages.current?.thumbnails, Modifier.matchParentSize())
                        if (media.isClip) PlayGlyph()
                    }
                }
            }
        }
    }
}

/**
 * Sizes [content] to [shot]'s aspect ratio inside the space it is given: a phone frame for portrait media, rounded
 * corners and a hairline for landscape. [content] receives the modifier to apply.
 */
@Composable
fun MediaFrame(shot: Screenshot, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val portrait = shot.aspectRatio < 1f
    val corner = mediaFrameCorner(shot)
    val frame = if (portrait) {
        Modifier
            .fillMaxHeight()
            .aspectRatio(shot.aspectRatio)
            .border(4.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .padding(4.dp)
            .clip(RoundedCornerShape(corner))
    } else {
        Modifier
            .aspectRatio(shot.aspectRatio)
            .clip(RoundedCornerShape(corner))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corner))
    }
    Box(modifier, contentAlignment = Alignment.Center) { content(frame) }
}

/**
 * The corner radius [MediaFrame] clips [shot] to, for content that a Compose clip only reaches as a rectangle (a
 * cover clip's HTML video).
 */
fun mediaFrameCorner(shot: Screenshot): Dp = if (shot.aspectRatio < 1f) 18.dp else 10.dp

/** A text-only project's stand-in for a cover: its initials, large, in the accent. */
@Composable
private fun Monogram(title: String, modifier: Modifier = Modifier) {
    val initials = remember(title) {
        title.split(' ').filter { it.isNotEmpty() && it.first().isLetterOrDigit() }.take(2).joinToString("") { it.first().uppercase() }
    }
    Box(
        modifier
            .size(96.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Tall enough to read a phone screen on the card, short enough that the card is not all cover. */
private val CardStageHeight = 280.dp

private val previewProject = Project(
    id = "vistago",
    title = "Vistago",
    icon = "vistago.webp",
    summary = "An offline-first AI travel companion for Android: plan trips, translate through the camera, and keep travel memories.",
    tagline = "An offline-first AI travel companion: plan, translate and remember trips",
    period = "Aug – Sep 2026",
    visibility = RepoVisibility.Private,
    cover = Screenshot("vistago-cover.webp", "Splash", 740, 1600),
    coverClip = Screenshot("vistago-splash.webp", "Launch", 332, 720),
    engineering = Engineering(builtWith = listOf("Kotlin", "Jetpack Compose", "Room"), platforms = listOf("Android")),
)

@Preview(widthDp = 420)
@Composable
private fun ProjectCardPreview() = PortfolioTheme {
    ProjectCard(previewProject, playing = { false }, onOpen = {})
}

@Preview(widthDp = 420)
@Composable
private fun ProjectCardDarkPreview() = PortfolioTheme(darkTheme = true) {
    ProjectCard(
        previewProject.copy(engineering = Engineering(platforms = listOf("Android", "iOS", "Web", "Desktop")), stack = listOf("Kotlin", "KMP")),
        playing = { false },
        onOpen = {},
    )
}

/** No media and no engineering: initials on the stage, the summary cut to one line's length, stack as chips. */
@Preview(widthDp = 420)
@Composable
private fun TextOnlyProjectCardPreview() = PortfolioTheme {
    ProjectCard(
        Project("field-notes", "Field Notes", "A text-only project with no media, so its initials stand in for the cover on the card.", stack = listOf("Node.js", "SQLite")),
        playing = { false },
        onOpen = {},
    )
}
