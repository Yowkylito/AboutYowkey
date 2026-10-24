package dev.yowkey.portfolio.ui.image

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.yowkey.portfolio.model.GalleryItem
import kotlinx.coroutines.CancellationException

/**
 * Card images and viewer media, cached separately so a few big images never evict the strip. Both are weighed
 * in decoded bytes (see [ViewerMedia.byteCount]).
 *
 * @property thumbnails card thumbnails, clip posters and cover heroes, keyed by path
 * @property viewer full-size stills and clips, keyed by gallery item so the loader knows which decoder to use
 * @property covers cover clips playing on cards and on the detail screen, keyed by path. Their own cache, so
 *   scrolling past a column of animated covers never evicts the viewer's open page, and a separate instance from
 *   the viewer's, so the two never step one clip's bitmap at once
 * @property decodeGate shared by every clip player: one frame decode per display frame (see [ClipDecodeGate])
 */
class ScreenshotImages(
    val thumbnails: LruLoader<String, ImageBitmap>,
    val viewer: LruLoader<GalleryItem, ViewerMedia>,
    val covers: LruLoader<String, AnimatedClip>,
    val decodeGate: ClipDecodeGate = ClipDecodeGate(),
) {
    companion object {
        /**
         * Every thumbnail on the page is ~5 MB decoded today; this leaves room for a cover hero per project
         * (960×540, 2 MB each) without scrolling back re-decoding them.
         */
        const val THUMBNAIL_BUDGET: Long = 24L * 1024 * 1024

        /**
         * Three of the largest full-size stills (1600×900, 5.8 MB each): the viewer's page and both neighbours.
         * A clip weighs its encoded file plus one decoded frame (~1.2 MB for the current one), so it costs less
         * than one still and shares the same budget.
         */
        const val VIEWER_BUDGET: Long = 18L * 1024 * 1024

        /**
         * Four cover clips at the script's limits (700 KB encoded + one 720×405 frame, 1.9 MB each): the two that
         * may play at once and the two last scrolled past, so scrolling back resumes them without a re-download
         * or re-parse. Today's clips weigh 0.94–1.17 MB each (see READINESS.md).
         */
        const val COVER_CLIP_BUDGET: Long = 8L * 1024 * 1024
    }
}

/** Null in previews and tests: images then stay as their sized placeholder. */
val LocalScreenshotImages = staticCompositionLocalOf<ScreenshotImages?> { null }

/**
 * Draws the image at [path] from [loader], or a placeholder of the same size until it arrives (or if it fails).
 * The caller sizes this, so the layout is final before any bytes load. The fade reads its progress in
 * `graphicsLayer`, so it costs no recomposition. An image already in [loader] (a row scrolled back into view) is
 * drawn whole from the first frame, with neither placeholder nor fade.
 */
@Composable
fun ScreenshotImage(
    path: String,
    contentDescription: String,
    loader: LruLoader<String, ImageBitmap>?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val cached = remember(path, loader) { loader?.peek(path) }
    val bitmap by produceState(cached, path, loader) {
        value = cached ?: try {
            loader?.get(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // a missing image keeps its placeholder; the tests guard that every listed file ships
        }
    }
    FadingImage(bitmap, contentDescription, modifier, contentScale, fadeIn = bitmap !== cached)
}

/**
 * [image] fading in over a placeholder of the caller's size; the placeholder alone while [image] is null. Without
 * [fadeIn] the image is there at once.
 */
@Composable
internal fun FadingImage(
    image: ImageBitmap?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fadeIn: Boolean = true,
) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { this.contentDescription = contentDescription },
    ) {
        image?.let { loaded ->
            val alpha = remember(loaded) { Animatable(if (fadeIn) 0f else 1f) }
            LaunchedEffect(loaded) { alpha.animateTo(1f, tween(220)) }
            Image(
                bitmap = loaded,
                contentDescription = null, // described once, on the container
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}
