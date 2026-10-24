package dev.yowkey.portfolio.ui.sections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.ui.image.LocalScreenshotImages
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * The hero's portrait: the cut-out photo at [path] standing on a tinted stage at the end side, head clear of the
 * stage's top, with a soft light behind the head that sets it off the stage. Nothing else: the still picture is clean,
 * as a professional headshot wants. The figure is drawn at the stage's full height, or smaller when the cut-out is
 * wider than [HeroStage.FIGURE_MAX_WIDTH] of it (a head-and-shoulders crop), on the same floor.
 *
 * The first time it is in (its image loaded and half of it on screen) it makes the hero's [entrance], once per page
 * load: the figure slides in from just past the stage's start edge and glides into its place on a long ease-out,
 * never past it, a few faint echoes close behind it that fade out before it settles ([HeroMotion]). If the intro had
 * to go ahead without it, it fades in where it stands instead. There is no replay: coming back into view, a pointer
 * or a press leave it as it is. With reduced motion only the still picture is drawn.
 *
 * The caller sizes it, and the stage alone is drawn until the image loads, so nothing shifts; nothing it draws leaves
 * its box or the stage's rounded bottom corners. Everything that moves is read in the draw phase, so the entrance
 * never recomposes.
 */
@Composable
fun HeroPortrait(path: String, name: String, entrance: HeroEntrance, modifier: Modifier = Modifier) {
    val loader = LocalScreenshotImages.current?.thumbnails
    // Started from the cache, as ScreenshotImage is: back at the top, the figure is there in the first frame.
    val cached = remember(path, loader) { loader?.peek(path) }
    val image by produceState(cached, path, loader) {
        value = try {
            loader?.get(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // the stage stays on its own; SiteContentTest guards that the listed file ships
        }
    }
    val reducedMotion = LocalReducedMotion.current
    var onScreen by remember { mutableStateOf(false) }
    // Shown already once this page load (the hero composed again on coming back): there from the first frame.
    val appear = remember { Animatable(if (entrance.portraitShown) 1f else 0f) }
    LaunchedEffect(image, onScreen, reducedMotion) {
        when {
            image == null -> Unit
            reducedMotion -> appear.snapTo(1f)
            !onScreen -> Unit
            // The first time in, the entrance's clock carries it (or it came in on the static page): shown at once.
            entrance.portraitArrives() -> appear.snapTo(1f)
            // The intro went ahead without it: it fades in where it stands.
            appear.value < 1f -> appear.animateTo(1f, tween(AppearMillis))
        }
    }

    val scheme = MaterialTheme.colorScheme
    val stageTop = scheme.primaryContainer
    val stageBottom = lerp(scheme.primaryContainer, scheme.background, 0.6f)
    val accent = scheme.primary
    Spacer(
        modifier
            .semantics {
                contentDescription = "Portrait of $name"
                role = Role.Image
            }
            .onVisibilityChanged(minFractionVisible = 0.5f) { onScreen = it }
            .drawWithCache {
                val radius = StageRadius.toPx()
                val stageY = size.height * HeroStage.STAGE_TOP
                val stage = Path().apply {
                    addRoundRect(RoundRect(0f, stageY, size.width, size.height, CornerRadius(radius)))
                }
                // The figure may rise above the stage but never past its rounded bottom corners.
                val frame = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f, top = 0f, right = size.width, bottom = size.height,
                            topLeftCornerRadius = CornerRadius.Zero, topRightCornerRadius = CornerRadius.Zero,
                            bottomRightCornerRadius = CornerRadius(radius), bottomLeftCornerRadius = CornerRadius(radius),
                        ),
                    )
                }
                val stageFill = Brush.verticalGradient(listOf(stageTop, stageBottom), startY = stageY, endY = size.height)
                val silhouette = ColorFilter.tint(accent, BlendMode.SrcIn)
                val inset = HeroStage.FIGURE_INSET_DP.dp.toPx()
                // The light behind the head stays where the head comes to rest, from the first frame: steady, soft.
                val box = size.height * HeroStage.FIGURE_MAX_WIDTH
                val light = Offset(size.width - inset - box * (1f - FaceX), size.height * FaceY)
                val glow = box * GlowReach
                val lightFill = Brush.radialGradient(listOf(accent.copy(alpha = GlowAlpha), Color.Transparent), light, glow)
                onDrawBehind {
                    drawPath(stage, stageFill)
                    clipPath(stage) { drawCircle(lightFill, radius = glow, center = light) }
                    val figure = image ?: return@onDrawBehind
                    val fade = appear.value
                    // The entrance's clock while it carries the portrait; at its end the figure is in place.
                    val ms = if (entrance.portraitOnClock) entrance.clock.value else HeroMotion.END_MS.toFloat()
                    val (width, height) = HeroStage.figureSize(size.height, figure.width.toFloat() / figure.height)
                    val top = size.height - height
                    val rest = size.width - width - inset
                    // The slide's way in: from just past the stage's start edge (the figure's end edge at 0) to rest.
                    val travel = rest + width
                    val dstSize = IntSize(width.roundToInt(), height.roundToInt())
                    clipPath(frame) {
                        // The trail: each echo where the figure was a moment before, faint, gone before it settles.
                        val trail = HeroMotion.trail(ms) * fade
                        if (trail > 0f) {
                            for (k in HeroMotion.ECHOES downTo 1) {
                                val x = rest - (1f - HeroMotion.dash(ms - k * HeroMotion.ECHO_LAG_MS)) * travel
                                if (x + width <= 0f) continue // not on the stage yet
                                drawImage(
                                    image = figure,
                                    dstOffset = IntOffset(x.roundToInt(), top.roundToInt()),
                                    dstSize = dstSize,
                                    alpha = EchoAlphas[k - 1] * trail,
                                    colorFilter = silhouette,
                                )
                            }
                        }
                        val x = rest - (1f - HeroMotion.dash(ms)) * travel
                        drawImage(
                            image = figure,
                            dstOffset = IntOffset(x.roundToInt(), top.roundToInt()),
                            dstSize = dstSize,
                            alpha = fade,
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
            },
    )
}

private val StageRadius = 24.dp

/** Where the face is in the figure's box, as shares of its width and of the stage's height: the light is there. */
private const val FaceX = 0.46f
private const val FaceY = 0.3f

/** The light behind the head: how far it reaches, as a share of the figure's box, and how strong it is at its middle. */
private const val GlowReach = 1.1f
private const val GlowAlpha = 0.16f

/** The trail's echoes, nearest first. */
private val EchoAlphas = floatArrayOf(0.2f, 0.12f, 0.06f)

private const val AppearMillis = 240
