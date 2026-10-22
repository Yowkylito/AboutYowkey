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
import kotlinx.coroutines.CancellationException

/** Card thumbnails and full-size viewer images, cached separately so a few big images never evict the strip. */
class ScreenshotImages(
    val thumbnails: LruLoader<String, ImageBitmap>,
    val fullSize: LruLoader<String, ImageBitmap>,
)

/** Null in previews and tests: images then stay as their sized placeholder. */
val LocalScreenshotImages = staticCompositionLocalOf<ScreenshotImages?> { null }

/**
 * Draws the image at [path] from [loader], or a placeholder of the same size until it arrives (or if it fails).
 * The caller sizes this, so the layout is final before any bytes load. The fade reads its progress in
 * `graphicsLayer`, so it costs no recomposition.
 *
 * [previewPath] (usually the cached thumbnail) is drawn underneath while [path] loads, so a full-size image
 * sharpens in place instead of popping in over an empty box.
 */
@Composable
fun ScreenshotImage(
    path: String,
    contentDescription: String,
    loader: LruLoader<String, ImageBitmap>?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    previewPath: String? = null,
    previewLoader: LruLoader<String, ImageBitmap>? = null,
) {
    val bitmap by rememberBitmap(path, loader)
    val preview by rememberBitmap(previewPath, previewLoader)
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { this.contentDescription = contentDescription },
    ) {
        preview?.let { image ->
            Image(image, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
        bitmap?.let { image ->
            val alpha = remember(image) { Animatable(0f) }
            LaunchedEffect(image) { alpha.animateTo(1f, tween(220)) }
            Image(
                bitmap = image,
                contentDescription = null, // described once, on the container
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}

@Composable
private fun rememberBitmap(path: String?, loader: LruLoader<String, ImageBitmap>?) =
    produceState<ImageBitmap?>(null, path, loader) {
        if (path == null) return@produceState
        value = try {
            loader?.get(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // a missing image keeps its placeholder; the tests guard that every listed file ships
        }
    }
