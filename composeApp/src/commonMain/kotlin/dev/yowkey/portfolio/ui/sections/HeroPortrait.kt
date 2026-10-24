package dev.yowkey.portfolio.ui.sections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The hero's portrait, screaming to the side: the cut-out figure at [path] stands on a tinted stage at the end side,
 * head clear of the stage's top, shouting toward the start, a soft glow at the face. Behind the figure its
 * silhouette smears away in accent echoes that melt into waves the farther they go, as the sky does in Munch's
 * Scream. That much is the still picture, so it reads as a shout even without motion.
 *
 * The first time it is in (its image loaded and half of it on screen) it makes the hero's [entrance], once per page
 * load: the figure dashes in from just past the stage's start edge and lands in its place with a short run-on and
 * settle, the trail streaming out behind it as its motion trail (each echo where the figure was a moment before,
 * stretched with the speed), and the scream's swell is the impact. If the intro had to go ahead without it, it fades
 * in where it stands instead.
 *
 * After that the scream plays each time at least half of the portrait comes back on screen (the trail folds back
 * behind the figure while it is off screen, so it bursts out again), and on a pointer coming over it or a press: a
 * hard attack that shakes the figure and splits its colour while the glow blooms, a burst that runs out along the
 * trail as a ripple, each echo reaching farther and melting deeper as it passes, then a fade back to the still
 * picture. Nothing is drawn as arcs or rings. With reduced motion only the still picture is drawn.
 *
 * The caller sizes it, and the stage alone is drawn until the image loads, so nothing shifts; nothing it draws leaves
 * its box or the stage's rounded bottom corners. Everything that moves is read in the draw phase, so neither the
 * entrance nor a scream recomposes.
 */
@Composable
fun HeroPortrait(path: String, name: String, entrance: HeroEntrance, modifier: Modifier = Modifier) {
    val loader = LocalScreenshotImages.current?.thumbnails
    val image by produceState<ImageBitmap?>(null, path, loader) {
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
    val appear = remember { Animatable(0f) }
    // How far the trail reaches: 0 is folded behind the figure, 1 is out.
    val spread = remember { Animatable(0f) }
    // One scream runs 0 → 1; 1 is at rest, where the swell is spent and the waves are back where they started.
    val scream = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(image, onScreen, reducedMotion) {
        when {
            image == null -> Unit
            reducedMotion -> {
                appear.snapTo(1f)
                spread.snapTo(1f)
                scream.snapTo(1f)
            }
            // Off screen the trail folds behind the figure, so it bursts out again; not while the entrance moves it.
            !onScreen -> if (!entrance.movesPortrait) {
                spread.snapTo(0f)
                scream.snapTo(1f)
            }
            // The first time in, the entrance's clock carries it: shown, the trail out, the swell the landing's.
            entrance.portraitArrives() -> {
                appear.snapTo(1f)
                spread.snapTo(1f)
                scream.snapTo(1f)
            }
            // Back in view mid-entrance: the clock carries on with it.
            entrance.movesPortrait -> Unit
            else -> {
                if (appear.value < 1f) appear.animateTo(1f, tween(AppearMillis))
                launch { spread.animateTo(1f, tween(SpreadMillis, easing = EaseOutCubic)) }
                scream.snapTo(0f)
                scream.animateTo(1f, tween(ScreamMillis, easing = LinearEasing))
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val stageTop = scheme.primaryContainer
    val stageBottom = lerp(scheme.primaryContainer, scheme.background, 0.6f)
    val accent = scheme.primary
    // The colour split's far fringe: navy on the light stage, pale blue on the dark one.
    val ink = scheme.onPrimaryContainer
    Spacer(
        modifier
            .semantics {
                contentDescription = "Portrait of $name"
                role = Role.Image
            }
            .onVisibilityChanged(minFractionVisible = 0.5f) { onScreen = it }
            .pointerInput(reducedMotion) {
                if (reducedMotion) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val type = awaitPointerEvent().type
                        // Not consumed: the hero's text selection and the page's scrolling see the same events.
                        if (type != PointerEventType.Enter && type != PointerEventType.Press) continue
                        // Mid-entrance, mid-scream or mid-arrival the gesture is ignored; undispatched, so a press
                        // arriving with its enter in the same frame finds the scream already started.
                        if (image == null || !onScreen || entrance.movesPortrait || scream.value < 1f || spread.value < 1f) continue
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            scream.snapTo(0f)
                            scream.animateTo(1f, tween(ScreamMillis, easing = LinearEasing))
                        }
                    }
                }
            }
            .drawWithCache {
                val radius = StageRadius.toPx()
                val stageY = size.height * StageTopFraction
                val stage = Path().apply {
                    addRoundRect(RoundRect(0f, stageY, size.width, size.height, CornerRadius(radius)))
                }
                // The figure and all it throws off may rise above the stage but never past its rounded bottom corners.
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
                val fringe = ColorFilter.tint(ink, BlendMode.SrcIn)
                onDrawBehind {
                    drawPath(stage, stageFill)
                    val figure = image ?: return@onDrawBehind
                    val fade = appear.value
                    val out = spread.value
                    val t = scream.value
                    // The entrance's clock while it carries the portrait; at its end all it moves is at rest.
                    val ms = if (entrance.portraitOnClock) entrance.clock.value else HeroMotion.END_MS.toFloat()
                    val screamed = t * ScreamMillis
                    // The landing's swell or a later scream's: only one of them is ever under way.
                    val swell = max(HeroMotion.landing(ms), HeroMotion.swellAt(screamed, ScreamMillis, echo = 0))
                    val shook = HeroMotion.shake((if (ms < HeroMotion.END_MS) ms - HeroMotion.IMPACT_AT_MS else screamed) / 1000f)
                    val shakeX = HeroMotion.SHAKE_DP.dp.toPx() * swell * shook.first
                    val shakeY = HeroMotion.SHAKE_DP.dp.toPx() * swell * shook.second

                    val height = size.height
                    val width = height * figure.width / figure.height
                    val rest = size.width - width - FigureInset.toPx()
                    // The dash's way in: from just past the stage's start edge (the figure's end edge at 0) to rest.
                    val travel = rest + width
                    val x = rest - (1f - HeroMotion.dash(ms)) * travel + shakeX
                    val face = Offset(x + width * FaceX, shakeY + height * FaceY)
                    val figureSize = IntSize(width.roundToInt(), height.roundToInt())

                    clipPath(stage) {
                        // The light the shout throws on the stage blooms with it: a soft fill, never a ring.
                        // It comes in with the figure, from nothing at the dash's start.
                        val glow = width * 1.2f * (1f + GlowBloom * swell)
                        val lit = (0.2f + 0.35f * swell) * fade * min(1f, HeroMotion.dash(ms))
                        drawCircle(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = lit), Color.Transparent),
                                center = face,
                                radius = glow,
                            ),
                            radius = glow,
                            center = face,
                        )
                    }
                    clipPath(frame) {
                        // The trail, farthest first so each nearer echo paints over the one behind it. Each is wider
                        // than the last and its waves deeper and shorter, so the outline melts as it goes. In the
                        // dash each echo is where the figure was a moment before, smeared back along its way as far
                        // as the speed then stretched it, so the trail streams out behind the figure and closes up as
                        // it stops. A swell reaches each echo a little after the one in front of it, so its burst
                        // runs out along the trail as a ripple, each echo reaching farther and melting deeper.
                        val phase = (t + HeroMotion.landingProgress(ms)) * WaveCycles * 2f * PI.toFloat()
                        for (k in TrailAlphas.size downTo 1) {
                            val then = ms - k * HeroMotion.ECHO_LAG_MS
                            val heard = max(HeroMotion.landing(ms, echo = k), HeroMotion.swellAt(screamed, ScreamMillis, echo = k))
                            val speed = HeroMotion.speed(then)
                            val reach = out * (1f + HeroMotion.BURST * heard)
                            val echoWidth = width * (1f + TrailStretch * k * out) * (1f + HeroMotion.SMEAR * speed)
                            val echoX = rest - (1f - HeroMotion.dash(then)) * travel + shakeX + width - echoWidth -
                                width * TrailStep * k * reach
                            if (echoX + echoWidth <= 0f) continue // not on the stage yet
                            drawEcho(
                                image = figure,
                                x = echoX,
                                y = shakeY,
                                width = echoWidth,
                                height = height,
                                amplitude = (CalmWave.toPx() + MeltWave.toPx() * k.toFloat().pow(1.3f)) * out *
                                    (1f + 1.5f * max(heard, speed)),
                                wavelength = height * (0.52f - 0.03f * k),
                                phase = phase + k * 0.8f,
                                slice = SliceHeight.toPx(),
                                alpha = TrailAlphas[k - 1] * fade,
                                filter = silhouette,
                            )
                        }
                        // The colour splits apart on the figure while the swell shakes it.
                        if (swell > 0.01f) {
                            val split = HeroMotion.SPLIT_DP.dp.toPx() * swell
                            listOf(-split to silhouette, split to fringe).forEach { (dx, filter) ->
                                drawImage(
                                    image = figure,
                                    dstOffset = IntOffset((x + dx).roundToInt(), shakeY.roundToInt()),
                                    dstSize = figureSize,
                                    alpha = 0.85f * swell * fade,
                                    colorFilter = filter,
                                )
                            }
                        }
                        drawImage(
                            image = figure,
                            dstOffset = IntOffset(x.roundToInt(), shakeY.roundToInt()),
                            dstSize = figureSize,
                            alpha = fade,
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
            },
    )
}

/**
 * One echo: [image] as a flat silhouette in bands [slice] tall, each band pushed sideways along a sine wave, so the
 * outline ripples. Bands are cut at whole source rows and placed at rounded edges that meet, so no seam shows.
 */
private fun DrawScope.drawEcho(
    image: ImageBitmap,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    amplitude: Float,
    wavelength: Float,
    phase: Float,
    slice: Float,
    alpha: Float,
    filter: ColorFilter,
) {
    val rows = image.height
    val scale = height / rows
    val step = max(1, (slice / scale).roundToInt())
    val dstWidth = width.roundToInt()
    var top = 0
    while (top < rows) {
        val bottom = min(rows, top + step)
        val y0 = (top * scale).roundToInt()
        val y1 = (bottom * scale).roundToInt()
        val dx = amplitude * sin((y0 + y1) / 2f / wavelength * 2f * PI.toFloat() + phase)
        drawImage(
            image = image,
            srcOffset = IntOffset(0, top),
            srcSize = IntSize(image.width, bottom - top),
            dstOffset = IntOffset((x + dx).roundToInt(), (y + y0).roundToInt()),
            dstSize = IntSize(dstWidth, y1 - y0),
            alpha = alpha,
            colorFilter = filter,
        )
        top = bottom
    }
}

/** The stage starts this far down, so the head stands clear of it. */
private const val StageTopFraction = 0.26f
private val StageRadius = 24.dp
private val FigureInset = 20.dp

/** Where the face is in the cut-out, as fractions of its width and height: the sound and the glow come from here. */
private const val FaceX = 0.46f
private const val FaceY = 0.3f

/** Nearest to farthest. */
private val TrailAlphas = floatArrayOf(0.55f, 0.44f, 0.34f, 0.25f, 0.17f, 0.1f, 0.05f)

/** Each echo's step from the last, and how much wider each is, as fractions of the figure's width. */
private const val TrailStep = 0.095f
private const val TrailStretch = 0.05f
private val CalmWave = 1.5.dp
private val MeltWave = 1.6.dp
private val SliceHeight = 3.dp
private const val WaveCycles = 3

/** How much wider the glow at the face grows at the swell's height. */
private const val GlowBloom = 0.3f

private const val AppearMillis = 240
private const val SpreadMillis = 700
private const val ScreamMillis = 1800

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
