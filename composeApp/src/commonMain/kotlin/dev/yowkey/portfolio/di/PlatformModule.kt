package dev.yowkey.portfolio.di

import org.koin.core.module.Module

/**
 * What each platform provides itself: the [Navigator][dev.yowkey.portfolio.ui.navigation.Navigator], which is the
 * browser's history on the web and an in-memory stack on the desktop. Started next to [appModule].
 */
expect val platformModule: Module
