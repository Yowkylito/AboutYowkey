package dev.yowkey.portfolio.di

import dev.yowkey.portfolio.data.HttpSiteApi
import dev.yowkey.portfolio.data.SiteApi
import dev.yowkey.portfolio.data.UrlConnectionTransport
import dev.yowkey.portfolio.ui.navigation.InMemoryNavigator
import dev.yowkey.portfolio.ui.navigation.Navigator
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What the Android app (`:androidApp`) tells the shared code when it starts Koin, as a single in its own module.
 *
 * @param countsVisits whether the guest view counts this visit, like a browser: only while the owner is not signed in
 * @param userAgent what the worker sees, with the app's version
 * @param rewriteUrl where a debug build sends the worker's requests instead (a local stand-in); identity otherwise
 */
class AndroidHost(
    val countsVisits: Boolean = true,
    val userAgent: String = "AboutYowkey-android",
    val rewriteUrl: (String) -> String = { it },
)

/** An in-memory back stack, which the system Back gesture pops, and the worker over `HttpURLConnection`. */
actual val platformModule: Module = module {
    single<Navigator> { InMemoryNavigator() }
    single {
        val host = getOrNull<AndroidHost>() ?: AndroidHost()
        UrlConnectionTransport(host.userAgent, host.rewriteUrl)
    }
    single<SiteApi> {
        HttpSiteApi(get<UrlConnectionTransport>(), countsVisits = (getOrNull<AndroidHost>() ?: AndroidHost()).countsVisits)
    }
}
