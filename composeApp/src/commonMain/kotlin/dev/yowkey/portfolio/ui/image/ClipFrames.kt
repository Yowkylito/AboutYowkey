package dev.yowkey.portfolio.ui.image

/** The frame-scheduling rules of [AnimatedClip], kept free of Skia so they are unit-tested on their own. */
object ClipFrames {

    /** Skia's "no frame": decode from a cleared bitmap (or let Skia rebuild the frames this one depends on). */
    const val NO_FRAME = -1

    /**
     * Browsers show a frame of 10 ms or less for 100 ms (a GIF-era convention encoders rely on), so a clip plays
     * at the same speed here as in the static page's `<img>`.
     */
    fun delayMillis(durationMillis: Int): Int = if (durationMillis <= 10) 100 else durationMillis

    fun next(frame: Int, frameCount: Int): Int = (frame + 1) % frameCount

    /**
     * Which frame Skia may build [frame] on top of, given that the bitmap holds [held]. Reusing it means only
     * the changed rectangle is decoded, not the whole canvas, which is what keeps one reusable bitmap cheap.
     *
     * @param required the frame [frame] is drawn over ([NO_FRAME] when it is a full, independent frame)
     * @param heldRestoresPrevious whether [held] is disposed by restoring what was under it; Skia cannot start
     *   from such a frame, so it rebuilds from [required] itself
     */
    fun priorFrame(frame: Int, held: Int, required: Int, heldRestoresPrevious: Boolean): Int = when {
        required == NO_FRAME -> NO_FRAME
        held in required until frame && !heldRestoresPrevious -> held
        else -> NO_FRAME
    }
}
