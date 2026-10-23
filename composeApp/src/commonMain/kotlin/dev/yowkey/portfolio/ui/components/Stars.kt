package dev.yowkey.portfolio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A five-pointed star, drawn (no icon font to download): filled when it counts, outlined when it does not. */
@Composable
fun StarGlyph(filled: Boolean, color: Color, modifier: Modifier = Modifier, glyphSize: Dp = 24.dp) {
    Spacer(
        modifier.size(glyphSize).drawWithCache {
            val star = starPath(size)
            val outline = Stroke(width = (glyphSize.value / 15).dp.toPx(), join = StrokeJoin.Round)
            onDrawBehind { if (filled) drawPath(star, color) else drawPath(star, color, style = outline) }
        },
    )
}

/** [rating] of five stars, lit from the left, read as one node: "4 of 5 stars". */
@Composable
fun StarRow(rating: Int, modifier: Modifier = Modifier, glyphSize: Dp = 16.dp) {
    val lit = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.outline
    Row(modifier.semantics { contentDescription = "$rating of 5 stars" }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (star in 1..5) StarGlyph(filled = star <= rating, color = if (star <= rating) lit else dim, glyphSize = glyphSize)
    }
}

private fun starPath(size: Size): Path {
    val c = size.center
    val outer = size.minDimension / 2
    val inner = outer * 0.42f
    return Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -PI / 2 + i * PI / 5
            val x = c.x + r * cos(angle).toFloat()
            val y = c.y + r * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
