package dev.yowkey.portfolio.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigatorTest {

    @Test
    fun fragmentsRoundTrip() {
        assertEquals("#/projects/about-yowkey", Route.Project("about-yowkey").fragment)
        assertEquals("", Route.Home.fragment)
        listOf(Route.Home, Route.Project("a"), Route.Project("eagles-watch-2")).forEach { route ->
            assertEquals(route, Route.parse(route.fragment))
        }
    }

    @Test
    fun anythingButAWellFormedProjectRouteIsHome() {
        assertEquals(Route.Project("vistago"), Route.parse("#/projects/vistago/"))
        listOf("", "#", "#projects", "#/projects/", "#/projects/Vistago", "#/projects/a/b", "#/projects/<x>", "#/other/a")
            .forEach { assertEquals(Route.Home, Route.parse(it), it) }
    }

    @Test
    fun backReturnsToTheListAndADeepLinkGoesHomeInsteadOfLeaving() {
        val navigator = InMemoryNavigator()
        navigator.open(Route.Project("a"))
        assertEquals(Route.Project("a"), navigator.route.value)
        navigator.back()
        assertEquals(Route.Home, navigator.route.value)

        val deepLinked = InMemoryNavigator(Route.Project("b"))
        deepLinked.back()
        assertEquals(Route.Home, deepLinked.route.value)
        deepLinked.back()
        assertEquals(Route.Home, deepLinked.route.value, "back at home stays home")
    }

    @Test
    fun replaceSwapsWithoutAddingAStep() {
        val navigator = InMemoryNavigator()
        navigator.open(Route.Project("ghost"))
        navigator.replace(Route.Home)
        assertEquals(Route.Home, navigator.route.value)
        navigator.open(Route.Project("a"))
        navigator.open(Route.Project("a")) // a double tap is one step
        navigator.back()
        assertEquals(Route.Home, navigator.route.value)
    }
}
