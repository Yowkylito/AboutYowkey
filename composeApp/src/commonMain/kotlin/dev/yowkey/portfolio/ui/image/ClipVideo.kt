package dev.yowkey.portfolio.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Whether this platform plays a clip's 60 fps `.mp4` twin (see `Screenshot.video`) instead of its WebP. The web
 * does, with the browser's video decoder: hardware-decoded off the UI thread, where a WebP frame costs the UI thread
 * 7–8 ms (see [ClipPlayer]), too much to do 60 times a second.
 */
internal expect val playsClipVideo: Boolean

/**
 * Plays the video at [path] (relative to composeResources) muted and in a loop while [playing], filling the bounds it
 * is given. On the web this is an HTML `<video>` placed over the canvas, so Compose cannot draw on top of it: use it
 * only where nothing overlaps the media (the viewer), and round its corners with [cornerRadius], since a Compose
 * clip only reaches it as a rectangle. Pointer input passes through to the Compose content underneath, so the
 * viewer's swipes and taps still work over it. [onError] is called if the video cannot be loaded or decoded.
 */
@Composable
internal expect fun ClipVideo(path: String, playing: Boolean, cornerRadius: Dp, onError: () -> Unit, modifier: Modifier)
