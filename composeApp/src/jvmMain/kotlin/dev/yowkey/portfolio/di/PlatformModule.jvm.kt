package dev.yowkey.portfolio.di

import dev.yowkey.portfolio.ui.navigation.InMemoryNavigator
import dev.yowkey.portfolio.ui.navigation.Navigator
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<Navigator> { InMemoryNavigator() }
}
