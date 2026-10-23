package dev.yowkey.portfolio.di

import org.koin.core.module.Module

/**
 * What each platform provides itself: the [Navigator][dev.yowkey.portfolio.ui.navigation.Navigator], which is the
 * browser's history on the web and an in-memory stack on the desktop, and the [SiteApi][dev.yowkey.portfolio.data.SiteApi]
 * over the platform's HTTP client (the browser's `fetch`; `java.net.http` on the desktop, whose window is the
 * owner's own and so reads the visit count without being a visit). Started next to [appModule].
 */
expect val platformModule: Module
