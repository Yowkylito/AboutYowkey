package dev.yowkey.portfolio.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** The desktop app has no video player, so it plays every clip's WebP, as before. */
internal actual val playsClipVideo: Boolean = false

/** Never called on the desktop ([playsClipVideo] is false). */
@Composable
internal actual fun ClipVideo(path: String, playing: Boolean, cornerRadius: Dp, onError: () -> Unit, modifier: Modifier) {
}
