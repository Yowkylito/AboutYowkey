package dev.yowkey.portfolio

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.yowkey.portfolio.di.appModule
import dev.yowkey.portfolio.di.platformModule
import org.koin.core.context.startKoin

fun main() {
    startKoin { modules(appModule, platformModule) }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Yowkey — Portfolio",
            state = rememberWindowState(width = 1280.dp, height = 860.dp),
        ) {
            App(platformLabel = "the JVM on your desktop")
        }
    }
}
