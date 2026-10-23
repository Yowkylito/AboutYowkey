package dev.yowkey.portfolio.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.PlatformKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Small line glyphs, drawn rather than taken from an icon font or library: nothing extra to download on the web,
 * and they scale with the density. Each is decorative; the text beside it carries the meaning.
 */

/** A platform's glyph: a robot head, a phone, a globe, a monitor, a server rack, or a dot for anything else. */
@Composable
fun PlatformGlyph(kind: PlatformKind, color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 14.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind { drawPlatform(kind, color, stroke) }
        },
    )
}

private fun DrawScope.drawPlatform(kind: PlatformKind, color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    when (kind) {
        PlatformKind.Android -> {
            // A domed head, outlined, with two antennae and two eyes.
            drawArc(
                color, 180f, 180f, useCenter = true,
                topLeft = Offset(w * 0.1f, h * 0.34f), size = Size(w * 0.8f, h * 0.8f), style = stroke,
            )
            drawLine(color, Offset(w * 0.3f, h * 0.4f), Offset(w * 0.2f, h * 0.16f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.7f, h * 0.4f), Offset(w * 0.8f, h * 0.16f), stroke.width, StrokeCap.Round)
            drawCircle(color, w * 0.065f, Offset(w * 0.37f, h * 0.6f))
            drawCircle(color, w * 0.065f, Offset(w * 0.63f, h * 0.6f))
        }
        PlatformKind.Ios -> {
            drawRoundRect(color, Offset(w * 0.24f, h * 0.06f), Size(w * 0.52f, h * 0.88f), CornerRadius(w * 0.12f), style = stroke)
            drawLine(color, Offset(w * 0.42f, h * 0.17f), Offset(w * 0.58f, h * 0.17f), stroke.width, StrokeCap.Round)
        }
        PlatformKind.Web -> {
            val r = w * 0.43f
            val c = Offset(w / 2, h / 2)
            drawCircle(color, r, c, style = stroke)
            drawOval(color, Offset(c.x - r * 0.45f, c.y - r), Size(r * 0.9f, r * 2), style = stroke)
            drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), stroke.width)
        }
        PlatformKind.Desktop -> {
            drawRoundRect(color, Offset(w * 0.06f, h * 0.12f), Size(w * 0.88f, h * 0.58f), CornerRadius(w * 0.08f), style = stroke)
            drawLine(color, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.5f, h * 0.86f), stroke.width)
            drawLine(color, Offset(w * 0.3f, h * 0.88f), Offset(w * 0.7f, h * 0.88f), stroke.width, StrokeCap.Round)
        }
        PlatformKind.Server -> {
            listOf(0.1f, 0.54f).forEach { top ->
                drawRoundRect(color, Offset(w * 0.1f, h * top), Size(w * 0.8f, h * 0.36f), CornerRadius(w * 0.08f), style = stroke)
                drawCircle(color, w * 0.06f, Offset(w * 0.27f, h * (top + 0.18f)))
            }
        }
        PlatformKind.Other -> drawCircle(color, w * 0.2f, Offset(w / 2, h / 2))
    }
}

/** A right-pointing arrow, for "View project". */
@Composable
fun ArrowGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 16.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val head = Path().apply {
                moveTo(size.width * 0.55f, size.height * 0.25f)
                lineTo(size.width * 0.82f, size.height * 0.5f)
                lineTo(size.width * 0.55f, size.height * 0.75f)
            }
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                drawLine(color, Offset(size.width * 0.18f, size.height / 2), Offset(size.width * 0.8f, size.height / 2), stroke.width, StrokeCap.Round)
                drawPath(head, color, style = stroke)
            }
        },
    )
}

/** A left-pointing arrow, for the detail screen's back button. */
@Composable
fun BackGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 20.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val head = Path().apply {
                moveTo(size.width * 0.45f, size.height * 0.22f)
                lineTo(size.width * 0.17f, size.height * 0.5f)
                lineTo(size.width * 0.45f, size.height * 0.78f)
            }
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                drawLine(color, Offset(size.width * 0.2f, size.height / 2), Offset(size.width * 0.84f, size.height / 2), stroke.width, StrokeCap.Round)
                drawPath(head, color, style = stroke)
            }
        },
    )
}

/** An up-pointing arrow, for Back to top: [BackGlyph] turned a quarter. */
@Composable
fun UpGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 20.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val head = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.45f)
                lineTo(size.width * 0.5f, size.height * 0.17f)
                lineTo(size.width * 0.78f, size.height * 0.45f)
            }
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                drawLine(color, Offset(size.width / 2, size.height * 0.2f), Offset(size.width / 2, size.height * 0.84f), stroke.width, StrokeCap.Round)
                drawPath(head, color, style = stroke)
            }
        },
    )
}

/** A tick in a ring, for the quality checklist. */
@Composable
fun CheckGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 18.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val tick = Path().apply {
                moveTo(size.width * 0.3f, size.height * 0.52f)
                lineTo(size.width * 0.45f, size.height * 0.66f)
                lineTo(size.width * 0.71f, size.height * 0.37f)
            }
            val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                drawCircle(color.copy(alpha = 0.14f))
                drawPath(tick, color, style = stroke)
            }
        },
    )
}

/** The theme switch's glyphs: a sun for the light theme, a crescent moon for the dark one. */
@Composable
fun ThemeGlyph(sun: Boolean, color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 16.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = size.width
            val c = Offset(w / 2, size.height / 2)
            if (sun) {
                val rays = (0 until 8).map { i ->
                    val angle = (i * PI / 4).toFloat()
                    Offset(cos(angle), sin(angle))
                }
                onDrawBehind {
                    drawCircle(color, w * 0.19f, c, style = stroke)
                    rays.forEach { dir ->
                        drawLine(color, c + dir * (w * 0.33f), c + dir * (w * 0.45f), stroke.width, StrokeCap.Round)
                    }
                }
            } else {
                // A disc with a second one cut out of its upper right: the crescent's inner edge.
                val r = w * 0.36f
                val disc = Path().apply { addOval(Rect(c, r)) }
                val cut = Path().apply { addOval(Rect(c + Offset(r * 0.6f, -r * 0.6f), r * 0.9f)) }
                val moon = Path().apply { op(disc, cut, PathOperation.Difference) }
                onDrawBehind { drawPath(moon, color, style = stroke) }
            }
        },
    )
}

/**
 * A push pin, leaning as if just pressed in, for a pinned note's badge: a round head on a short body, a wide collar,
 * and the needle, filled so it still reads at 12 dp.
 */
@Composable
fun PinGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 12.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val w = size.width
            val h = size.height
            val pin = Path().apply {
                addRoundRect(RoundRect(Rect(w * 0.34f, h * 0.04f, w * 0.66f, h * 0.18f), CornerRadius(w * 0.07f)))
                addRect(Rect(w * 0.39f, h * 0.16f, w * 0.61f, h * 0.44f))
                // The collar flares out from the body, the way a pin's plastic does.
                moveTo(w * 0.39f, h * 0.42f)
                lineTo(w * 0.61f, h * 0.42f)
                lineTo(w * 0.8f, h * 0.6f)
                lineTo(w * 0.2f, h * 0.6f)
                close()
            }
            val needle = 1.3.dp.toPx()
            onDrawBehind {
                rotate(35f) {
                    drawPath(pin, color)
                    drawLine(color, Offset(w * 0.5f, h * 0.6f), Offset(w * 0.5f, h * 0.96f), needle, StrokeCap.Round)
                }
            }
        },
    )
}

/**
 * An eye, for the bar's visit count: an almond outline, its two lids meeting in points at the corners, round a solid
 * pupil, so it still reads at 14 dp.
 */
@Composable
fun EyeGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 14.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val w = size.width
            val h = size.height
            val lids = Path().apply {
                moveTo(w * 0.04f, h * 0.5f)
                cubicTo(w * 0.28f, h * 0.14f, w * 0.72f, h * 0.14f, w * 0.96f, h * 0.5f)
                cubicTo(w * 0.72f, h * 0.86f, w * 0.28f, h * 0.86f, w * 0.04f, h * 0.5f)
                close()
            }
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                drawPath(lids, color, style = stroke)
                drawCircle(color, w * 0.15f, Offset(w / 2, h / 2))
            }
        },
    )
}

/** A cross, for the star sheet's close button. */
@Composable
fun CloseGlyph(color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 18.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val stroke = 1.8.dp.toPx()
            val a = size.width * 0.24f
            val b = size.width * 0.76f
            onDrawBehind {
                drawLine(color, Offset(a, a), Offset(b, b), stroke, StrokeCap.Round)
                drawLine(color, Offset(b, a), Offset(a, b), stroke, StrokeCap.Round)
            }
        },
    )
}
