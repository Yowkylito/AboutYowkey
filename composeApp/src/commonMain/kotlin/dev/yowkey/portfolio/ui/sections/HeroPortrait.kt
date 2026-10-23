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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * head clear of the stage's top, shouting toward the start. Sound lines leave the face, and behind the figure its
 * silhouette smears away in accent echoes that melt into waves the farther they go, as the sky does in Munch's
 * Scream. That much is the still picture, so it reads as a shout even without motion.
 *
 * The scream itself plays each time at least half of the portrait comes on screen (the trail folds back behind the
 * figure while it is off screen, so it bursts out again), and on a pointer coming over it or a press: a hard attack
 * that shakes the figure and splits its colour, shockwaves rolling out from the face, the trail stretching and
 * rippling, then a fade back to the still picture. With reduced motion only the still picture is drawn.
 *
 * The caller sizes it, and the stage alone is drawn until the image loads, so nothing shifts. Everything that
 * moves is read in the draw phase, so the animation never recomposes.
 */
@Composable
fun HeroPortrait(path: String, name: String, modifier: Modifier = Modifier) {
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
            !onScreen -> {
                spread.snapTo(0f)
                scream.snapTo(1f)
            }
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
    // The sound lines and the far colour fringe: navy on the light stage, pale blue on the dark one.
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
                        // Mid-scream (or mid-arrival) the gesture is ignored; undispatched, so a press arriving with
                        // its enter in the same frame finds the scream already started.
                        if (image == null || !onScreen || scream.value < 1f || spread.value < 1f) continue
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
                val shoutStrokes = ShoutStrokes.map { Stroke(width = it.toPx(), cap = StrokeCap.Round) }
                onDrawBehind {
                    drawPath(stage, stageFill)
                    val figure = image ?: return@onDrawBehind
                    val fade = appear.value
                    val out = spread.value
                    val t = scream.value
                    val swell = swellAt(t)
                    val seconds = t * ScreamMillis / 1000f
                    val shakeX = Shake.toPx() * swell * sin(seconds * 2f * PI.toFloat() * 21f)
                    val shakeY = Shake.toPx() * 0.5f * swell * sin(seconds * 2f * PI.toFloat() * 15f + 1f)

                    val height = size.height
                    val width = height * figure.width / figure.height
                    val x = size.width - width - FigureInset.toPx() + shakeX
                    val face = Offset(x + width * FaceX, shakeY + height * FaceY)
                    val figureSize = IntSize(width.roundToInt(), height.roundToInt())

                    clipPath(stage) {
                        val glow = width * 1.2f
                        drawCircle(
                            Brush.radialGradient(
                                listOf(accent.copy(alpha = (0.2f + 0.3f * swell) * fade), Color.Transparent),
                                center = face,
                                radius = glow,
                            ),
                            radius = glow,
                            center = face,
                        )
                    }
                    clipPath(frame) {
                        // The trail, farthest first so each nearer echo paints over the one behind it. Each is wider
                        // than the last and its waves deeper and shorter, so the outline melts as it goes.
                        val reach = out * (1f + 0.6f * swell)
                        val phase = t * WaveCycles * 2f * PI.toFloat()
                        for (k in TrailAlphas.size downTo 1) {
                            val echoWidth = width * (1f + TrailStretch * k * out)
                            drawEcho(
                                image = figure,
                                x = x + width - echoWidth - width * TrailStep * k * reach,
                                y = shakeY,
                                width = echoWidth,
                                height = height,
                                amplitude = (CalmWave.toPx() + MeltWave.toPx() * k.toFloat().pow(1.3f)) * out * (1f + 1.5f * swell),
                                wavelength = height * (0.52f - 0.03f * k),
                                phase = phase + k * 0.8f,
                                slice = SliceHeight.toPx(),
                                alpha = TrailAlphas[k - 1] * fade,
                                filter = silhouette,
                            )
                        }
                        // Shockwaves, only while screaming: each leaves the face thick and fades thin as it rolls out.
                        repeat(Shockwaves) { i ->
                            val p = (t - ShockwaveFirst - i * ShockwaveEvery) / ShockwaveLife
                            if (p !in 0f..1f) return@repeat
                            val r = width * 0.25f + p * size.width * 0.9f
                            drawArc(
                                color = accent.copy(alpha = (1f - p).pow(1.6f) * 0.8f * fade),
                                startAngle = 180f - ShockwaveSweep / 2,
                                sweepAngle = ShockwaveSweep,
                                useCenter = false,
                                topLeft = Offset(face.x - r, face.y - r),
                                size = Size(r * 2, r * 2),
                                style = Stroke(width = (5f - 4f * p).dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                        // The shout's sound lines, in the still picture too; under the figure, which covers their ends.
                        repeat(ShoutRadii.size) { i ->
                            val r = width * ShoutRadii[i] * (1f + 0.15f * swell)
                            drawArc(
                                color = ink.copy(alpha = ShoutAlphas[i] * out * fade),
                                startAngle = 180f - ShoutSweeps[i] / 2,
                                sweepAngle = ShoutSweeps[i],
                                useCenter = false,
                                topLeft = Offset(face.x - r, face.y - r),
                                size = Size(r * 2, r * 2),
                                style = shoutStrokes[i],
                            )
                        }
                        // The colour splits apart on the figure while the scream shakes it.
                        if (swell > 0.01f) {
                            val split = Split.toPx() * swell
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

/** The scream's loudness at [t]: a hard attack to full over the first tenth, then a long fall back to silence. */
private fun swellAt(t: Float): Float = when {
    t <= 0f || t >= 1f -> 0f
    t < Attack -> 1f - (1f - t / Attack).pow(2)
    else -> (1f - (t - Attack) / (1f - Attack)).pow(2)
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

/** The shout's three sound lines, nearest first: radius as a fraction of the figure's width, sweep, ink, weight. */
private val ShoutRadii = floatArrayOf(0.26f, 0.38f, 0.5f)
private val ShoutSweeps = floatArrayOf(50f, 64f, 78f)
private val ShoutAlphas = floatArrayOf(0.85f, 0.6f, 0.38f)
private val ShoutStrokes = listOf(3.dp, 2.5.dp, 2.dp)

private const val Shockwaves = 5
private const val ShockwaveFirst = 0.04f
private const val ShockwaveEvery = 0.12f
private const val ShockwaveLife = 0.45f
private const val ShockwaveSweep = 110f

private val Shake = 3.dp
private val Split = 6.dp
private const val Attack = 0.1f

private const val AppearMillis = 240
private const val SpreadMillis = 700
private const val ScreamMillis = 1800

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
