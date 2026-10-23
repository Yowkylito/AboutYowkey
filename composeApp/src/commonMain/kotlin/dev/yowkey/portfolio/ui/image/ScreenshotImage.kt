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
 */
class ScreenshotImages(
    val thumbnails: LruLoader<String, ImageBitmap>,
    val viewer: LruLoader<GalleryItem, ViewerMedia>,
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
    }
}

/** Null in previews and tests: images then stay as their sized placeholder. */
val LocalScreenshotImages = staticCompositionLocalOf<ScreenshotImages?> { null }

/**
 * Draws the image at [path] from [loader], or a placeholder of the same size until it arrives (or if it fails).
 * The caller sizes this, so the layout is final before any bytes load. The fade reads its progress in
 * `graphicsLayer`, so it costs no recomposition.
 */
@Composable
fun ScreenshotImage(
    path: String,
    contentDescription: String,
    loader: LruLoader<String, ImageBitmap>?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<ImageBitmap?>(null, path, loader) {
        value = try {
            loader?.get(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // a missing image keeps its placeholder; the tests guard that every listed file ships
        }
    }
    FadingImage(bitmap, contentDescription, modifier, contentScale)
}

/** [image] fading in over a placeholder of the caller's size; the placeholder alone while [image] is null. */
@Composable
internal fun FadingImage(
    image: ImageBitmap?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { this.contentDescription = contentDescription },
    ) {
        image?.let { loaded ->
            val alpha = remember(loaded) { Animatable(0f) }
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
