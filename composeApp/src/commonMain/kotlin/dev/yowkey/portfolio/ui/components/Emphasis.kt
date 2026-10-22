package dev.yowkey.portfolio.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import dev.yowkey.portfolio.text.Emphasis
import dev.yowkey.portfolio.text.EmphasisKind
import dev.yowkey.portfolio.ui.theme.EmphasisStyles
import dev.yowkey.portfolio.ui.theme.LocalEmphasis

/** [text] with its numeric facts styled (see [Emphasis]). Pure, so it is unit-tested and safe to remember. */
fun emphasize(text: String, styles: EmphasisStyles): AnnotatedString = buildAnnotatedString {
    Emphasis.segments(text).forEach { (run, kind) ->
        when (kind) {
            null -> append(run)
            EmphasisKind.Strong -> withStyle(styles.strong) { append(run) }
            EmphasisKind.Figure -> withStyle(styles.figure) { append(run) }
        }
    }
}

/** A Text whose wins and counts stand out; the annotated string is built once per text, not per recomposition. */
@Composable
fun EmphasizedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val styles = LocalEmphasis.current
    val annotated = remember(text, styles) { emphasize(text, styles) }
    Text(annotated, modifier, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
