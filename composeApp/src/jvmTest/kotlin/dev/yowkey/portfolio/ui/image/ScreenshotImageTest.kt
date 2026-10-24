package dev.yowkey.portfolio.ui.image

import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A lazy row that scrolls away takes its images with it, and the cache keeps them for its return. One the cache
 * already holds is drawn in the row's first frame, as it was left, not as a placeholder fading back in.
 */
class ScreenshotImageTest {

    private val red = ImageBitmap(8, 8).also { image ->
        Canvas(image).drawRect(Rect(0f, 0f, 8f, 8f), Paint().apply { color = Color.Red })
    }

    @Test
    fun aCachedImageShowsInTheFirstFrame() {
        val loader = LruLoader<String, ImageBitmap>(capacity = 4, dispatcher = Dispatchers.Unconfined) { red }
        runBlocking { loader.get("shot.webp") }
        ImageComposeScene(40, 40, Density(1f)) {
            PortfolioTheme { ScreenshotImage("shot.webp", "A shot", loader, Modifier.size(40.dp)) }
        }.use { scene ->
            val first = Bitmap.makeFromImage(scene.render(0))
            assertEquals(Color.Red, Color(first.getColor(20, 20)), "the cached image, whole, in the first frame")
        }
    }

    @Test
    fun anImageStillToLoadFadesInOverItsPlaceholder() {
        val loader = LruLoader<String, ImageBitmap>(capacity = 4, dispatcher = Dispatchers.Unconfined) { red }
        ImageComposeScene(40, 40, Density(1f)) {
            PortfolioTheme { ScreenshotImage("shot.webp", "A shot", loader, Modifier.size(40.dp)) }
        }.use { scene ->
            val first = Color(Bitmap.makeFromImage(scene.render(0)).getColor(20, 20))
            assertEquals(1f, first.alpha)
            assertEquals(true, first.red < 0.99f || first.green > 0.01f, "not yet the image: $first")
            var time = 0L
            repeat(30) { scene.render(16_000_000L * ++time) }
            assertEquals(Color.Red, Color(Bitmap.makeFromImage(scene.render(16_000_000L * ++time)).getColor(20, 20)))
        }
    }
}
