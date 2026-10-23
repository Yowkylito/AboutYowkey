package dev.yowkey.portfolio.ui.image

import dev.yowkey.portfolio.ui.image.ClipFrames.NO_FRAME
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipFramesTest {

    @Test
    fun sequentialPlaybackBuildsOnTheHeldFrame() {
        assertEquals(4, ClipFrames.priorFrame(frame = 5, held = 4, required = 4, heldRestoresPrevious = false))
        // The frame needs 2 underneath; 4 already has 2 composited in, so it is a valid start too.
        assertEquals(4, ClipFrames.priorFrame(frame = 5, held = 4, required = 2, heldRestoresPrevious = false))
    }

    @Test
    fun independentFramesAndUnusableHeldFramesStartFromNothing() {
        assertEquals(NO_FRAME, ClipFrames.priorFrame(frame = 0, held = 53, required = NO_FRAME, heldRestoresPrevious = false))
        assertEquals(NO_FRAME, ClipFrames.priorFrame(frame = 5, held = 1, required = 2, heldRestoresPrevious = false))
        assertEquals(NO_FRAME, ClipFrames.priorFrame(frame = 5, held = 7, required = 2, heldRestoresPrevious = false))
        assertEquals(NO_FRAME, ClipFrames.priorFrame(frame = 5, held = 4, required = 4, heldRestoresPrevious = true))
        assertEquals(NO_FRAME, ClipFrames.priorFrame(frame = 3, held = NO_FRAME, required = 2, heldRestoresPrevious = false))
    }

    @Test
    fun loopsAndTimesFramesLikeBrowsers() {
        assertEquals(0, ClipFrames.next(53, 54))
        assertEquals(1, ClipFrames.next(0, 54))
        assertEquals(83, ClipFrames.delayMillis(83))
        assertEquals(100, ClipFrames.delayMillis(0))
        assertEquals(100, ClipFrames.delayMillis(10))
        assertEquals(11, ClipFrames.delayMillis(11))
    }
}
