@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.yowkey.portfolio.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [Navigator] over the browser's history and the URL fragment (`#/projects/<id>`):
 * - opening a project pushes a history entry the app marks as its own, so the browser's Back button (and
 *   [back]) return to the list, whose scroll position the UI has kept;
 * - a deep link has no entry of ours before it, so [back] replaces the URL with the bare page instead of leaving
 *   the site;
 * - `popstate` and `hashchange` (Back, Forward, an edited address bar) feed [route]; the StateFlow drops repeats.
 */
class HashNavigator : Navigator {
    private val _route = MutableStateFlow(Route.parse(currentFragment()))
    override val route: StateFlow<Route> = _route.asStateFlow()

    init {
        onHistoryChange { _route.value = Route.parse(currentFragment()) }
    }

    override fun open(route: Route) {
        if (route == _route.value) return
        pushOwnEntry(urlFor(route))
        _route.value = route
    }

    override fun back() {
        // Our own entry: let the browser step back, exactly as its Back button would; popstate updates the route.
        if (isOwnEntry()) goBack() else replace(Route.Home)
    }

    override fun replace(route: Route) {
        replaceEntry(urlFor(route))
        _route.value = route
    }

    /** Path and query kept, only the fragment changes: the page is never reloaded. */
    private fun urlFor(route: Route): String = currentPathAndQuery() + route.fragment
}

private fun currentFragment(): String = js("window.location.hash")

private fun currentPathAndQuery(): String = js("window.location.pathname + window.location.search")

private fun pushOwnEntry(url: String): Unit = js("window.history.pushState({ yowkeyApp: true }, '', url)")

private fun replaceEntry(url: String): Unit = js("window.history.replaceState(null, '', url)")

private fun isOwnEntry(): Boolean = js("!!(window.history.state && window.history.state.yowkeyApp === true)")

private fun goBack(): Unit = js("window.history.back()")

private fun onHistoryChange(callback: () -> Unit): Unit = js(
    """{
        window.addEventListener('popstate', function () { callback(); });
        window.addEventListener('hashchange', function () { callback(); });
    }""",
)
