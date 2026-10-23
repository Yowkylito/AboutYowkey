package dev.yowkey.portfolio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion

/**
 * Room a host keeps at the page's bottom end for a button of its own, so Back to top floats above that button
 * instead of under it: the Android app's shortcut back to the owner view, while it shows.
 */
val LocalBottomEndClearance = compositionLocalOf { 0.dp }

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/**
 * The round Back to top button for the page's bottom end: the surface, a hairline outline and a soft shadow around
 * an up arrow. It fades and grows in while [visible] says so (read here, so only this button recomposes when the
 * answer flips) and goes the same way; under reduced motion it appears and goes at once. It keeps clear of the
 * system bars and the keyboard wherever its host has not already, and of [LocalBottomEndClearance].
 */
@Composable
fun BackToTop(visible: () -> Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible(),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
            .padding(bottom = LocalBottomEndClearance.current),
        enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(160)) + scaleIn(tween(220, easing = EaseOutCubic), initialScale = 0.6f),
        exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.6f),
        label = "back to top",
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = "Back to top"
                    role = Role.Button
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) { UpGlyph(MaterialTheme.colorScheme.onSurface) }
        }
    }
}
