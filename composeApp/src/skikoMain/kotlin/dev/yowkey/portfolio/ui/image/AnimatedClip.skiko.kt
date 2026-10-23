package dev.yowkey.portfolio.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.AnimationDisposalMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

internal actual fun decodeAnimatedClip(bytes: ByteArray): AnimatedClip = SkiaAnimatedClip.decode(bytes)

/** [AnimatedClip] with Skia's [Codec], on the desktop and the web (the targets Compose draws with Skia). */
private class SkiaAnimatedClip private constructor(private val data: Data, private val codec: Codec) : AnimatedClip {

    override val frameCount: Int = codec.frameCount
    private val frames = codec.framesInfo
    private val bitmap = Bitmap().apply { allocPixels(codec.imageInfo) }

    override val image: ImageBitmap = bitmap.asComposeImageBitmap()

    override var heldFrame: Int = ClipFrames.NO_FRAME
        private set

    override val byteCount: Long = data.size.toLong() + image.byteCount

    override fun delayMillis(frame: Int): Int = ClipFrames.delayMillis(frames[frame].duration)

    override fun show(frame: Int) {
        if (frame == heldFrame) return
        val held = heldFrame
        val prior = ClipFrames.priorFrame(
            frame = frame,
            held = held,
            required = frames[frame].requiredFrame,
            heldRestoresPrevious = held >= 0 && frames[held].disposalMethod == AnimationDisposalMode.RESTORE_PREVIOUS,
        )
        codec.readPixels(bitmap, frame, prior)
        bitmap.notifyPixelsChanged()
        heldFrame = frame
    }

    companion object {
        fun decode(bytes: ByteArray): AnimatedClip {
            val data = Data.makeFromBytes(bytes)
            val clip = SkiaAnimatedClip(data, Codec.makeFromData(data))
            require(clip.frameCount > 0) { "clip has no frames" }
            clip.show(0)
            return clip
        }
    }
}
