package dev.yowkey.portfolio.model

import dev.yowkey.portfolio.model.ModuleGraph.Edge
import dev.yowkey.portfolio.model.ModuleGraph.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleGraphTest {

    private fun m(name: String, vararg deps: String) = Module(name, "role", deps.toList())

    private fun ModuleGraph.Plan.names(layer: Int) = layers[layer].map {
        when (it) {
            is Slot.Node -> it.module.name
            is Slot.Lane -> "lane:${edges[it.edge].from}>${edges[it.edge].to}"
        }
    }

    private val app = listOf(
        m(":app", ":feature:a", ":feature:b", ":core:model"),
        m(":feature:a", ":core:data"),
        m(":feature:b", ":core:ui"),
        m(":core:data", ":core:model"),
        m(":core:ui"),
        m(":core:model"),
    )

    @Test
    fun everyModuleSitsAboveAllItsDependencies() {
        val plan = ModuleGraph.plan(app)
        assertEquals(app.map { it.name }.toSet(), plan.layerOf.keys, "every module once")
        app.forEach { module ->
            module.dependsOn.forEach { dep -> assertTrue(plan.layerOf.getValue(dep) < plan.layerOf.getValue(module.name), "${module.name} → $dep") }
        }
        assertEquals(listOf(":core:ui", ":core:model"), plan.names(0), "foundation first, authored order")
        // The longest path decides the layer: :app sits above :feature:a, which sits above :core:data.
        assertEquals(3, plan.layerOf[":app"])
    }

    @Test
    fun anArrowAlreadyImpliedByAnotherIsNotDrawn() {
        val edges = ModuleGraph.reducedEdges(app)
        assertTrue(Edge(":app", ":core:model") !in edges, ":app reaches :core:model through :feature:a")
        assertEquals(
            listOf(
                Edge(":app", ":feature:a"), Edge(":app", ":feature:b"), Edge(":feature:a", ":core:data"),
                Edge(":feature:b", ":core:ui"), Edge(":core:data", ":core:model"),
            ),
            edges,
        )
        assertEquals(listOf(Edge("a", "b")), ModuleGraph.reducedEdges(listOf(m("a", "b", "b", "ghost"), m("b"))), "repeats and unknowns drop")
    }

    @Test
    fun aLongArrowGetsALaneInEveryLayerItCrosses() {
        // jvm → site skips the di and ui layers; it must pass through them, not through their boxes.
        val plan = ModuleGraph.plan(
            listOf(m("jvm", "di", "site"), m("di", "ui"), m("ui", "data"), m("site", "model"), m("data", "model"), m("model")),
        )
        assertEquals(listOf(0, 1, 1, 2, 3, 4), listOf("model", "data", "site", "ui", "di", "jvm").map { plan.layerOf.getValue(it) })
        listOf(2, 3).forEach { layer -> assertTrue("lane:jvm>site" in plan.names(layer), "layer $layer: ${plan.names(layer)}") }
        assertTrue(plan.layers[1].none { it is Slot.Lane } && plan.layers[4].none { it is Slot.Lane })
        // site is on the right of data in layer 1 only if its lane keeps right above it, and vice versa: no crossing.
        val siteSide = plan.names(1).indexOf("site") > plan.names(1).indexOf("data")
        listOf(2, 3).forEach { layer ->
            val names = plan.names(layer)
            assertEquals(siteSide, names.indexOf("lane:jvm>site") > names.indexOfFirst { !it.startsWith("lane") }, "layer $layer: $names")
        }
    }

    @Test
    fun aLayerIsOrderedByWhereItsDependenciesSit() {
        // Authored backwards: b sits over y (right), a over x (left), so the layer above reads a, b.
        val plan = ModuleGraph.plan(listOf(m("b", "y"), m("a", "x"), m("x"), m("y")))
        assertEquals(listOf("x", "y"), plan.names(0))
        assertEquals(listOf("a", "b"), plan.names(1))
    }

    @Test
    fun noModulesNoLayers() {
        assertTrue(ModuleGraph.plan(emptyList()).layers.isEmpty())
        assertEquals(listOf("solo"), ModuleGraph.plan(listOf(m("solo"))).names(0))
    }

    @Test
    fun cyclesAreFoundWithTheirPath() {
        assertNull(ModuleGraph.findCycle(listOf(m("a", "b"), m("b", "c"), m("c"))))
        assertNull(ModuleGraph.findCycle(listOf(m("a", "b", "c"), m("b", "c"), m("c"))), "a diamond is not a cycle")
        assertEquals(listOf("b", "c", "b"), ModuleGraph.findCycle(listOf(m("a", "b"), m("b", "c"), m("c", "b"))))
        assertEquals(listOf("a", "a"), ModuleGraph.findCycle(listOf(m("a", "a"))))
        assertNull(ModuleGraph.findCycle(listOf(m("a", "ghost"))), "unknown names are the parser's other check")
    }
}
