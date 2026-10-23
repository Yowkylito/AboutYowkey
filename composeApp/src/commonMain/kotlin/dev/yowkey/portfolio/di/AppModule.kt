package dev.yowkey.portfolio.di

import dev.yowkey.portfolio.data.PortfolioRepository
import dev.yowkey.portfolio.data.ResourcePortfolioRepository
import dev.yowkey.portfolio.resources.Res
import androidx.compose.ui.graphics.ImageBitmap
import dev.yowkey.portfolio.ui.PortfolioViewModel
import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.ui.image.AnimatedClip
import dev.yowkey.portfolio.ui.image.LruLoader
import dev.yowkey.portfolio.ui.image.ScreenshotImages
import dev.yowkey.portfolio.ui.image.ViewerMedia
import dev.yowkey.portfolio.ui.image.byteCount
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Kotlin/Wasm has no IO dispatcher; Default is the right pool for decoding JSON on both targets.
    single<CoroutineDispatcher> { Dispatchers.Default }
    single<PortfolioRepository> {
        ResourcePortfolioRepository(
            readContent = { Res.readBytes("files/portfolio.json") },
            dispatcher = get(),
        )
    }
    single {
        ScreenshotImages(
            thumbnails = LruLoader(
                capacity = ScreenshotImages.THUMBNAIL_BUDGET,
                dispatcher = get(),
                weigh = ImageBitmap::byteCount,
            ) { path -> Res.readBytes(path).decodeToImageBitmap() },
            viewer = LruLoader<GalleryItem, ViewerMedia>(
                capacity = ScreenshotImages.VIEWER_BUDGET,
                dispatcher = get(),
                weigh = ViewerMedia::byteCount,
            ) { item ->
                val bytes = Res.readBytes(item.shot.fullPath)
                if (item.isClip) AnimatedClip.decode(bytes) else ViewerMedia.Still(bytes.decodeToImageBitmap())
            },
        )
    }
    viewModelOf(::PortfolioViewModel)
}
