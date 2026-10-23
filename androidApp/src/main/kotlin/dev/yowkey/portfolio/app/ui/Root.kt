package dev.yowkey.portfolio.app.ui

import androidx.activity.compose.BackHandler
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
import dev.yowkey.portfolio.ui.navigation.Navigator
import dev.yowkey.portfolio.ui.navigation.Route
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import org.koin.compose.koinInject

private enum class Face { Portfolio, SignIn, Admin }

/** The guest view, the sign-in screen or the admin view, in the portfolio's own theme. */
@Composable
fun YowkeyRoot(owner: OwnerViewModel, reducedMotion: Boolean) {
    val signedIn by owner.signedIn.collectAsState()
    val nav by owner.nav.collectAsState()
    val face = when {
        nav.portfolio -> Face.Portfolio
        !signedIn -> Face.SignIn
        else -> Face.Admin
    }
    PortfolioTheme {
        Crossfade(targetState = face, label = "face") { shown ->
            when (shown) {
                Face.Portfolio -> GuestView(signedIn = signedIn, reducedMotion = reducedMotion, onOwner = owner::showOwner)
                Face.SignIn -> SignInScreen(owner)
                Face.Admin -> AdminScreen(owner)
            }
        }
    }
}

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
        App(platformLabel = "Android on your device", reducedMotion = reducedMotion)
        if (signedIn && route == Route.Home) {
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
