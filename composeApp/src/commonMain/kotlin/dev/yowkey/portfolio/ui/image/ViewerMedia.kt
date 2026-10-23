package dev.yowkey.portfolio.ui.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.model.Screenshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.skia.AnimationDisposalMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import kotlin.math.roundToInt

/** What the viewer keeps decoded, weighed in bytes against one budget (see `ScreenshotImages.VIEWER_BUDGET`). */
sealed interface ViewerMedia {
    val byteCount: Long

    class Still(val image: ImageBitmap) : ViewerMedia {
        override val byteCount: Long get() = image.byteCount
    }
}

/** Decoded size of a bitmap: 4 bytes a pixel, which is what Skia allocates for N32. */
val ImageBitmap.byteCount: Long get() = width.toLong() * height * 4

/**
 * An animated WebP decoded one frame at a time into one reusable bitmap, with Skia's [Codec]. Only the encoded
 * file and that one frame are ever in memory: a 4.5 s, 54-frame clip at 480×271 holds ~0.7 MB encoded + 0.5 MB
 * of pixels, where decoding every frame up front would hold 28 MB.
 *
 * Not thread-safe: one player at a time steps it, which the viewer guarantees (a clip is one page).
 */
class AnimatedClip private constructor(private val data: Data, private val codec: Codec) : ViewerMedia {

    val frameCount: Int = codec.frameCount
    private val frames = codec.framesInfo
    private val bitmap = Bitmap().apply { allocPixels(codec.imageInfo) }

    /** The bitmap as Compose sees it. The wrapper is made once; drawing it reads the pixels as they are now. */
    val image: ImageBitmap = bitmap.asComposeImageBitmap()

    /** The frame the bitmap holds; it survives the viewer closing, so reopening resumes where it stopped. */
    var heldFrame: Int = ClipFrames.NO_FRAME
        private set

    override val byteCount: Long = data.size.toLong() + image.byteCount

    fun delayMillis(frame: Int): Int = ClipFrames.delayMillis(frames[frame].duration)

    /** Decodes [frame] into the bitmap, on top of the frame already there when the format allows it. */
    fun show(frame: Int) {
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
        /** Parses [bytes] and decodes the first frame, so the viewer has a picture the moment it gets the clip. */
        fun decode(bytes: ByteArray): AnimatedClip {
            val data = Data.makeFromBytes(bytes)
            val clip = AnimatedClip(data, Codec.makeFromData(data))
            require(clip.frameCount > 0) { "clip has no frames" }
            clip.show(0)
            return clip
        }
    }
}

/**
 * Plays [clip] in a loop while [playing], filling the bounds it is given (the caller sizes it to the clip's
 * aspect ratio). Stopped, it shows the frame it holds: the first one under reduced motion.
 *
 * Each frame is decoded inside a frame-clock callback, on the UI thread, just before that frame is drawn:
 * - Kotlin/Wasm has one thread, so there is nowhere else to decode on the web; on the JVM, decoding elsewhere
 *   would race the draw that reads this same bitmap, and avoiding that needs a second bitmap.
 * - Measured in Chrome 156 (Kotlin/Wasm, the shipped 480×271 clip): 7.0–8.0 ms a frame on average, 12.6 ms at
 *   worst. That fits the 16 ms frame at 60 Hz with room for the draw; at 120 Hz a decode frame can overrun 8 ms,
 *   which is why it only happens 12 times a second (the loop sleeps in `delay` between frames, it does not wake
 *   on every vsync) and why the viewer pauses the clip while the pager is being swiped. Frames only build on
 *   the held one ([ClipFrames.priorFrame]), so a clip whose frames change a small area decodes faster.
 * - The frame clock does not tick in a background tab, so decoding stops there too.
 * - Several players (two card covers, say) share one [ClipDecodeGate]: at most one decode per display frame, so
 *   their costs never add up inside one frame.
 *
 * The new frame reaches the screen by bumping a counter read only in `drawBehind`: no recomposition, no layout.
 */
@Composable
fun ClipPlayer(clip: AnimatedClip, playing: Boolean, modifier: Modifier = Modifier) {
    val shown = remember(clip) { mutableIntStateOf(clip.heldFrame) }
    val gate = LocalScreenshotImages.current?.decodeGate ?: remember { ClipDecodeGate() }
    LaunchedEffect(clip, playing, gate) {
        if (!playing || clip.frameCount < 2) return@LaunchedEffect
        var frame = clip.heldFrame
        var due = withFrameMillis { it }
        var shownAt = due
        while (true) {
            due += clip.delayMillis(frame)
            frame = ClipFrames.next(frame, clip.frameCount)
            // Wake a little early, then let the frame clock pick the vsync; `due` keeps the pace from drifting.
            delay((due - shownAt - EARLY_WAKE_MS).coerceAtLeast(0))
            val next = frame
            shownAt = gate.decodeInFrame {
                clip.show(next)
                shown.intValue = next
            }
            // After a stall (a hidden tab, a long frame) carry on from now instead of racing to catch up.
            if (shownAt - due > STALL_MS) due = shownAt
        }
    }
    Box(
        modifier.drawBehind {
            shown.intValue // the read that redraws on each new frame
            drawImage(
                clip.image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        },
    )
}

/** Runs [decode] inside the first display frame whose slot is free, and returns that frame's time. */
private suspend fun ClipDecodeGate.decodeInFrame(decode: () -> Unit): Long {
    while (true) {
        val at = withFrameMillis { now -> if (tryAcquire(now)) now.also { decode() } else NOT_DECODED }
        if (at != NOT_DECODED) return at
    }
}

private const val NOT_DECODED = Long.MIN_VALUE
private const val EARLY_WAKE_MS = 8L
private const val STALL_MS = 250L

/**
 * One viewer page: a still at full size, or a clip that plays while [playing]. A clip shows its poster (already
 * cached from the card) until it has loaded, so the page is never blank.
 *
 * Where the platform plays video ([playsClipVideo]) and the clip has a 60 fps twin, that video plays instead of the
 * WebP, which is then never downloaded; if the video fails to load, the WebP takes over. The video is drawn above
 * everything Compose draws, so it is only put up while [videoShown] (until then the page is the WebP's still, as
 * without a video), and its corners are rounded to [cornerRadius] to match the clip the caller gives this
 * composable.
 */
@Composable
fun ViewerMediaImage(
    item: GalleryItem,
    playing: Boolean,
    modifier: Modifier = Modifier,
    videoShown: Boolean = true,
    cornerRadius: Dp = 0.dp,
) {
    var videoFailed by remember(item) { mutableStateOf(false) }
    if (playsClipVideo && item.shot.video && videoShown && !videoFailed) {
        val images = LocalScreenshotImages.current
        Box(modifier) {
            // The poster shows until the video's first frame covers it.
            ScreenshotImage(item.cardPath, item.shot.caption, images?.thumbnails, Modifier.fillMaxSize(), ContentScale.Fit)
            ClipVideo(item.shot.videoPath, playing, cornerRadius, onError = { videoFailed = true }, modifier = Modifier.fillMaxSize())
        }
    } else {
        DecodedMedia(item, playing, modifier)
    }
}

/** A still, or a clip's WebP decoded frame by frame by [ClipPlayer]. */
@Composable
private fun DecodedMedia(item: GalleryItem, playing: Boolean, modifier: Modifier) {
    val images = LocalScreenshotImages.current
    val media by produceState<ViewerMedia?>(null, item, images) {
        value = try {
            images?.viewer?.get(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // a missing or undecodable file keeps the placeholder (or poster); the tests guard what ships
        }
    }
    val description = item.shot.caption
    when (val loaded = media) {
        is AnimatedClip -> Box(modifier) {
            // The poster under the clip carries the description, once.
            ScreenshotImage(item.cardPath, description, images?.thumbnails, Modifier.fillMaxSize(), ContentScale.Fit)
            ClipPlayer(loaded, playing, Modifier.fillMaxSize())
        }
        is ViewerMedia.Still -> FadingImage(loaded.image, description, modifier, ContentScale.Fit)
        null -> if (item.isClip) {
            ScreenshotImage(item.cardPath, description, images?.thumbnails, modifier, ContentScale.Fit)
        } else {
            FadingImage(null, description, modifier, ContentScale.Fit)
        }
    }
}

/**
 * A cover clip: its poster at once, and the clip over it while [playing]. The clip is not even downloaded until it
 * is first asked to play, so a card that never comes into view, or a visitor who asked for reduced motion, costs
 * only the poster. The caller sizes this to the clip's aspect ratio.
 *
 * Where the platform plays video ([playsClipVideo]) and [videoAllowed], a clip with a 60 fps twin plays that instead,
 * with its corners rounded to [cornerRadius] (see [ClipVideo]). The video is only put up while [playing]: it is
 * drawn above everything Compose draws, so a paused one would show through the viewer opened over the page. Paused,
 * the cover is its poster again. If the video fails to load, the WebP takes over. Otherwise, paused, the WebP holds
 * the frame it reached.
 */
@Composable
fun CoverClip(
    shot: Screenshot,
    playing: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    videoAllowed: Boolean = true,
) {
    var videoFailed by remember(shot) { mutableStateOf(false) }
    if (playsClipVideo && shot.video && videoAllowed && !videoFailed) {
        val images = LocalScreenshotImages.current
        Box(modifier) {
            ScreenshotImage(shot.posterPath, shot.caption, images?.thumbnails, Modifier.fillMaxSize())
            if (playing) {
                ClipVideo(shot.videoPath, playing = true, cornerRadius, onError = { videoFailed = true }, modifier = Modifier.fillMaxSize())
            }
        }
    } else {
        DecodedCoverClip(shot, playing, modifier)
    }
}

/** A cover clip's WebP, decoded frame by frame by [ClipPlayer] once it is first asked to play. */
@Composable
private fun DecodedCoverClip(shot: Screenshot, playing: Boolean, modifier: Modifier) {
    val images = LocalScreenshotImages.current
    var wanted by remember(shot) { mutableStateOf(playing) }
    if (playing && !wanted) SideEffect { wanted = true }
    val clip by produceState<AnimatedClip?>(null, shot, images, wanted) {
        if (!wanted) return@produceState
        value = try {
            images?.covers?.get(shot.fullPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // the poster stays; the tests guard that every listed clip decodes
        }
    }
    Box(modifier) {
        ScreenshotImage(shot.posterPath, shot.caption, images?.thumbnails, Modifier.fillMaxSize())
        clip?.let { ClipPlayer(it, playing, Modifier.fillMaxSize()) }
    }
}
