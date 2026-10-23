package dev.yowkey.portfolio.site

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Two tiny ffmpeg encodes: one the way `prepare_screenshots.py --video` writes, one the way it must not. */
class Mp4InfoTest {

    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/mp4/$name")) { "missing $name" }.readBytes()

    @Test
    fun readsAClipTheScriptWouldShip() {
        val info = Mp4Info.read(fixture("h264-60fps-index-first.mp4"))
        assertEquals("avc1", info.codec)
        assertEquals(32 to 64, info.width to info.height)
        assertEquals(15, info.frames)
        assertEquals(60.0, info.framesPerSecond, 0.01)
        assertFalse(info.hasAudio)
        assertTrue(info.indexFirst)
    }

    @Test
    fun seesASlowerFrameRateAndAnIndexAfterTheFrames() {
        val info = Mp4Info.read(fixture("h264-30fps-index-last.mp4"))
        assertEquals(48 to 32, info.width to info.height)
        assertEquals(30.0, info.framesPerSecond, 0.01)
        assertFalse(info.indexFirst)
    }

    @Test
    fun rejectsWhatIsNotAnMp4() {
        val webp = File("src/commonMain/composeResources/files/screens/vistago-splash.webp").readBytes()
        assertFailsWith<IllegalArgumentException> { Mp4Info.read(webp) }
        assertFailsWith<IllegalArgumentException> { Mp4Info.read(ByteArray(0)) }
    }
}
