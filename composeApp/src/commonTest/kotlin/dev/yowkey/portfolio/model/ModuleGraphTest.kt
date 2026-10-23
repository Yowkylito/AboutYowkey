package dev.yowkey.portfolio.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleGraphTest {

    private fun m(name: String, vararg deps: String) = Module(name, "role", deps.toList())

    private fun List<List<Module>>.names() = map { layer -> layer.map { it.name } }

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
        val layers = ModuleGraph.layers(app)
        val layerOf = layers.flatMapIndexed { index, layer -> layer.map { it.name to index } }.toMap()
        assertEquals(app.map { it.name }.sorted(), layerOf.keys.sorted(), "every module once")
        app.forEach { module ->
            module.dependsOn.forEach { dep -> assertTrue(layerOf.getValue(dep) < layerOf.getValue(module.name), "${module.name} → $dep") }
        }
        // The longest path decides the layer: :app sits above :feature:a, which sits above :core:data.
        assertEquals(
            listOf(listOf(":core:ui", ":core:model"), listOf(":feature:b", ":core:data"), listOf(":feature:a"), listOf(":app")),
            layers.names(),
            "foundation first, each layer in authored order",
        )
    }

    @Test
    fun aWideGraphKeepsItsLayersFlat() {
        // BubuProtect's shape: four modules share a layer, and an unused one sits in the foundation.
        val layers = ModuleGraph.layers(
            listOf(
                m("domain"), m("crypto"), m("data", "domain", "crypto"), m("session", "data", "crypto"),
                m("security", "domain"), m("shield", "domain"), m("otp"), m("backup", "crypto", "domain"),
                m("ui", "domain", "session", "security", "shield"), m("di", "data", "session", "ui"),
            ),
        )
        assertEquals(
            listOf(listOf("domain", "crypto", "otp"), listOf("data", "security", "shield", "backup"), listOf("session"), listOf("ui"), listOf("di")),
            layers.names(),
        )
    }

    @Test
    fun repeatsAndUnlistedDependenciesDoNotLiftAModule() {
        assertEquals(listOf(listOf("b"), listOf("a")), ModuleGraph.layers(listOf(m("a", "b", "b", "ghost"), m("b"))).names())
    }

    @Test
    fun noModulesNoLayers() {
        assertTrue(ModuleGraph.layers(emptyList()).isEmpty())
        assertEquals(listOf(listOf("solo")), ModuleGraph.layers(listOf(m("solo"))).names())
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
