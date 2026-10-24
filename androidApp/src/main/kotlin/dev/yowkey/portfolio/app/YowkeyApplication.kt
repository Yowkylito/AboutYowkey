package dev.yowkey.portfolio.app

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import dev.yowkey.portfolio.BuildConfig
import dev.yowkey.portfolio.app.owner.AndroidOwnerDevice
import dev.yowkey.portfolio.app.owner.OwnerDevice
import dev.yowkey.portfolio.app.owner.OwnerSession
import dev.yowkey.portfolio.app.owner.OwnerStore
import dev.yowkey.portfolio.app.owner.OwnerViewModel
import dev.yowkey.portfolio.app.owner.asAccess
import dev.yowkey.portfolio.app.push.AlertNotifier
import dev.yowkey.portfolio.app.push.PollWorker
import dev.yowkey.portfolio.data.UrlConnectionTransport
import dev.yowkey.portfolio.di.AndroidHost
import dev.yowkey.portfolio.di.appModule
import dev.yowkey.portfolio.di.platformModule
import dev.yowkey.portfolio.ui.theme.AndroidThemeStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Starts Koin with the portfolio's own modules (the guest view is the shared app, unchanged) and the owner's, then
 * the notification channels and, while signed in, the backup check. WorkManager starts on demand
 * ([Configuration.Provider]), so a worker never runs before Koin is up.
 */
class YowkeyApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        AndroidThemeStore.init(this)
        val store = OwnerStore(this)
        startKoin {
            androidContext(this@YowkeyApplication)
            modules(appModule, platformModule, ownerModule(store))
        }
        AlertNotifier.createChannels(this)
        if (store.signedIn.value) PollWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.ERROR)
            .build()

    private fun ownerModule(store: OwnerStore) = module {
        single { store }
        // Decided at start: the guest view counts its visit like a browser only while the owner is signed out.
        single {
            AndroidHost(
                countsVisits = !store.signedIn.value,
                userAgent = "AboutYowkey-android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})",
                rewriteUrl = DebugApi.rewriter(androidContext()),
            )
        }
        single { AlertNotifier(androidContext(), get()) }
        single { OwnerSession(androidContext(), get(), get<UrlConnectionTransport>(), get(), get()) }
        single<OwnerDevice> { AndroidOwnerDevice(androidContext(), get(), get()) }
        // The third get() is the activity's SavedStateHandle, which Koin makes from the view model's creation extras.
        viewModel { OwnerViewModel(get<OwnerSession>().asAccess(), get(), get()) }
    }
}
