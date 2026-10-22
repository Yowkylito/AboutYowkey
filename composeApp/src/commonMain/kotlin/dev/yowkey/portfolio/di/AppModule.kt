package dev.yowkey.portfolio.di

import dev.yowkey.portfolio.data.PortfolioRepository
import dev.yowkey.portfolio.data.ResourcePortfolioRepository
import dev.yowkey.portfolio.resources.Res
import androidx.compose.ui.graphics.ImageBitmap
import dev.yowkey.portfolio.ui.PortfolioViewModel
import dev.yowkey.portfolio.ui.image.LruLoader
import dev.yowkey.portfolio.ui.image.ScreenshotImages
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
        val decode: suspend (String) -> ImageBitmap = { path -> Res.readBytes(path).decodeToImageBitmap() }
        ScreenshotImages(
            // Thumbnails are ~200dp tall and small; every one on the page fits.
            thumbnails = LruLoader(capacity = 40, dispatcher = get(), load = decode),
            // Full-size images are several MB decoded; keep the viewer's page and its neighbours.
            fullSize = LruLoader(capacity = 3, dispatcher = get(), load = decode),
        )
    }
    viewModelOf(::PortfolioViewModel)
}
