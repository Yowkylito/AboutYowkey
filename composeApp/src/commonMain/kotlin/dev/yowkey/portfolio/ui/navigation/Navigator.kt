package dev.yowkey.portfolio.ui.navigation

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's two screens. Written into the URL's fragment on the web (`#/projects/<id>`), so a project can be
 * linked to and the browser's Back button leaves it; a fragment needs no server routing on a static host.
 */
@Immutable
sealed interface Route {
    /** The page with every section. */
    data object Home : Route

    /** One project's detail screen. */
    data class Project(val id: String) : Route

    /** The URL fragment for this route, `#` included; Home is no fragment at all. */
    val fragment: String
        get() = when (this) {
            Home -> ""
            is Project -> "$PROJECT_PREFIX$id"
        }

    companion object {
        private const val PROJECT_PREFIX = "#/projects/"
        private val ProjectId = Regex("[a-z0-9][a-z0-9-]*")

        /**
         * Reads a URL fragment such as `location.hash`. Anything that is not a well-formed project route is Home,
         * including the static page's own anchors (`#projects`), so an odd link never strands the visitor. A
         * well-formed id that no project has is still a [Project]: the screen sends it home once content is loaded.
         */
        fun parse(fragment: String): Route {
            val id = fragment.removePrefix(PROJECT_PREFIX).takeIf { fragment.startsWith(PROJECT_PREFIX) }
                ?.trimEnd('/')
            return if (id != null && ProjectId.matches(id)) Project(id) else Home
        }
    }
}

/**
 * Where the app is, and the moves between screens. The web keeps this in the browser's history (see
 * `HashNavigator` in wasmJsMain), the desktop in memory ([InMemoryNavigator]); the UI only sees this interface.
 * Called on the UI thread.
 */
interface Navigator {
    val route: StateFlow<Route>

    /** Opens [route] as a new step, so Back returns to where the visitor was. */
    fun open(route: Route)

    /** One step back. With nothing to go back to inside the app (a deep link), goes Home instead of leaving. */
    fun back()

    /** Swaps the current step for [route] without adding history, such as when a linked project does not exist. */
    fun replace(route: Route)
}

/** A plain back stack: the desktop app's navigator, and the tests'. */
class InMemoryNavigator(initial: Route = Route.Home) : Navigator {
    private val stack = ArrayDeque(listOf(initial))
    private val _route = MutableStateFlow(initial)
    override val route: StateFlow<Route> = _route.asStateFlow()

    override fun open(route: Route) {
        if (route == stack.last()) return
        stack.addLast(route)
        _route.value = route
    }

    override fun back() {
        if (stack.size > 1) stack.removeLast() else stack[0] = Route.Home
        _route.value = stack.last()
    }

    override fun replace(route: Route) {
        stack[stack.lastIndex] = route
        _route.value = route
    }
}
