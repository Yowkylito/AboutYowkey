package dev.yowkey.portfolio.model

/**
 * The module graph of [Engineering.modules] as the detail screen draws it. Pure, so the parser's cycle check and
 * the diagram's layers are unit-tested without Compose.
 */
object ModuleGraph {

    /**
     * The first dependency cycle found, as module names with the first repeated at the end (`a → b → a`), or null
     * when there is none. Dependencies on unlisted modules are ignored here; the parser reports them separately.
     */
    fun findCycle(modules: List<Module>): List<String>? {
        val byName = modules.associateBy { it.name }
        val done = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun visit(name: String): List<String>? {
            if (name in done) return null
            val open = path.indexOf(name)
            if (open >= 0) return path.subList(open, path.size) + name
            val module = byName[name] ?: return null
            path += name
            for (dependency in module.dependsOn) visit(dependency)?.let { return it }
            path.removeAt(path.lastIndex)
            done += name
            return null
        }

        for (module in modules) visit(module.name)?.let { return it }
        return null
    }

    /**
     * The modules in dependency layers, foundation first: layer 0 depends on nothing listed, and every other module
     * sits one layer above the highest of its dependencies, so everything a module uses is in a layer below it.
     * Each layer keeps the authored order; no layer is empty. The modules must be acyclic (the parser guarantees it).
     */
    fun layers(modules: List<Module>): List<List<Module>> {
        if (modules.isEmpty()) return emptyList()
        val byName = modules.associateBy { it.name }
        val depth = mutableMapOf<String, Int>()
        fun depthOf(name: String): Int = depth.getOrPut(name) {
            byName.getValue(name).dependsOn.filter { it in byName }.maxOfOrNull { depthOf(it) + 1 } ?: 0
        }
        // A module in layer n > 0 has a dependency in layer n - 1, so every layer up to the top one has a module.
        val byLayer = modules.groupBy { depthOf(it.name) }
        return List(byLayer.keys.max() + 1) { byLayer.getValue(it) }
    }
}
