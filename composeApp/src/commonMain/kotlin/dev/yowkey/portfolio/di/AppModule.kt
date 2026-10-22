package dev.yowkey.portfolio.di

import dev.yowkey.portfolio.data.PortfolioRepository
import dev.yowkey.portfolio.data.ResourcePortfolioRepository
import dev.yowkey.portfolio.resources.Res
import dev.yowkey.portfolio.ui.PortfolioViewModel
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
    viewModelOf(::PortfolioViewModel)
}
