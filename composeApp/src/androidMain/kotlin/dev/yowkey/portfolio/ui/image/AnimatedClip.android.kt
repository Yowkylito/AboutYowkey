package dev.yowkey.portfolio.ui.image

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun decodeAnimatedClip(bytes: ByteArray): AnimatedClip = FirstFrameClip(bytes)

/**
 * Android plays every clip's `.mp4` twin ([playsClipVideo]), so the WebP is only the fallback for a video that
 * fails to load. Android's decoders give no frame-by-frame access to an animated WebP, so the fallback is the clip's
 * first frame, held still: the poster's picture rather than a blank.
 */
private class FirstFrameClip(bytes: ByteArray) : AnimatedClip {

    override val image: ImageBitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "clip has no frames" }
        .asImageBitmap()

    override val frameCount: Int = 1

    override val heldFrame: Int = 0

    override val byteCount: Long = image.byteCount

    override fun delayMillis(frame: Int): Int = ClipFrames.delayMillis(0)

    override fun show(frame: Int) = Unit
}
