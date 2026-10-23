package dev.yowkey.portfolio.app.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.App
import dev.yowkey.portfolio.R
import dev.yowkey.portfolio.app.owner.OwnerViewModel
import dev.yowkey.portfolio.ui.components.LocalBottomEndClearance
import dev.yowkey.portfolio.ui.navigation.Navigator
import dev.yowkey.portfolio.ui.navigation.Route
import dev.yowkey.portfolio.ui.theme.AndroidThemeStore
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import org.koin.compose.koinInject

private enum class Face { Portfolio, SignIn, Admin }

/**
 * The guest view, the sign-in screen or the admin view, in the portfolio's own theme: light unless the visitor
 * switched the page to dark, which the admin view and the system bars then follow too.
 */
@Composable
fun YowkeyRoot(owner: OwnerViewModel, reducedMotion: Boolean) {
    val signedIn by owner.signedIn.collectAsState()
    val nav by owner.nav.collectAsState()
    val dark by AndroidThemeStore.dark.collectAsState()
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, dark) { activity?.applySystemBars(dark) }
    val face = when {
        nav.portfolio -> Face.Portfolio
        !signedIn -> Face.SignIn
        else -> Face.Admin
    }
    PortfolioTheme(darkTheme = dark) {
        Crossfade(targetState = face, label = "face") { shown ->
            when (shown) {
                Face.Portfolio -> GuestView(signedIn = signedIn, reducedMotion = reducedMotion, onOwner = owner::showOwner)
                Face.SignIn -> SignInScreen(owner)
                Face.Admin -> AdminScreen(owner)
            }
        }
    }
}

/** Edge to edge, with the bars' icons dark on the light theme and light on the dark one (not the system's mode). */
fun ComponentActivity.applySystemBars(dark: Boolean) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
        navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { dark },
    )
}

/** The owner's button at the guest view's bottom end: 40 dp tall on a 16 dp margin. */
private val OwnerButtonClearance = 56.dp

// The scrims Android puts behind three-button navigation by default (gesture navigation shows none).
private val LightNavigationScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * The portfolio exactly as the web shows it (the shared [App]), kept clear of the system bars and the keyboard.
 * System Back pops the app's own [Navigator] (a project's detail goes back to the page); on the page itself it
 * leaves the app, or, for the signed-in owner, returns to the admin view, as the small button in the corner does.
 */
@Composable
private fun GuestView(signedIn: Boolean, reducedMotion: Boolean, onOwner: () -> Unit) {
    val navigator: Navigator = koinInject()
    val route by navigator.route.collectAsState()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val ownerButton = signedIn && route == Route.Home
        // Back to top rises above the owner's button rather than hiding under it.
        CompositionLocalProvider(LocalBottomEndClearance provides if (ownerButton) OwnerButtonClearance else 0.dp) {
            App(platformLabel = "Android on your device", reducedMotion = reducedMotion)
        }
        if (ownerButton) {
            SmallFloatingActionButton(
                onClick = onOwner,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .semantics { contentDescription = "Back to the owner view" },
            ) {
                Icon(painterResource(R.drawable.ic_nav_dashboard), contentDescription = null)
            }
        }
    }
    BackHandler(enabled = route != Route.Home) { navigator.back() }
    BackHandler(enabled = signedIn && route == Route.Home, onBack = onOwner)
}
