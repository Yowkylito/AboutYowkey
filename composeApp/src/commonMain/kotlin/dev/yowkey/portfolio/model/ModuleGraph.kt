package dev.yowkey.portfolio.model

/**
 * The module graph of [Engineering.modules] as the detail screen draws it. Pure, so the parser's cycle check and
 * the diagram's layout are unit-tested without Compose.
 *
 * The layout is a small layered (Sugiyama-style) drawing:
 * 1. Only the [reducedEdges] are drawn: `a → c` is left out when `a → b → c` already shows it.
 * 2. Every module sits one layer above the highest of its dependencies, so each arrow points down.
 * 3. An arrow that skips layers gets a [Slot.Lane] in each layer it crosses: a narrow gap it runs through, so it
 *    never cuts across a box.
 * 4. Within a layer, slots are ordered by where what they point to sits in the layer below (the barycentre), which
 *    keeps arrows short and uncrossed where it can; ties keep the authored order.
 */
object ModuleGraph {

    /** An arrow from a module to one it depends on. */
    data class Edge(val from: String, val to: String)

    /** One position in a layer: a module's box, or a lane an arrow passes through. */
    sealed interface Slot {
        data class Node(val module: Module) : Slot
        data class Lane(val edge: Int) : Slot
    }

    /**
     * @property layers foundation first: layer 0 depends on nothing listed
     * @property edges the arrows; a [Slot.Lane] names one by its index here
     */
    data class Plan(val layers: List<List<Slot>>, val edges: List<Edge>) {
        /** Which layer each module is in. */
        val layerOf: Map<String, Int> by lazy {
            layers.flatMapIndexed { index, layer -> layer.filterIsInstance<Slot.Node>().map { it.module.name to index } }.toMap()
        }
    }

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
     * The direct dependencies worth an arrow: an edge `a → c` is dropped when another dependency of `a` already
     * reaches `c`. The modules must be acyclic (the parser guarantees it).
     */
    fun reducedEdges(modules: List<Module>): List<Edge> {
        val byName = modules.associateBy { it.name }
        val reach = mutableMapOf<String, Set<String>>()
        fun reachable(name: String): Set<String> = reach.getOrPut(name) {
            byName[name]?.dependsOn.orEmpty().filter { it in byName }.flatMap { reachable(it) + it }.toSet()
        }
        return modules.flatMap { module ->
            val direct = module.dependsOn.filter { it in byName }.distinct()
            direct.filter { dep -> direct.none { other -> other != dep && dep in reachable(other) } }
                .map { Edge(module.name, it) }
        }
    }

    /** The layered plan the diagram draws; empty for no modules. */
    fun plan(modules: List<Module>): Plan {
        if (modules.isEmpty()) return Plan(emptyList(), emptyList())
        val byName = modules.associateBy { it.name }
        val edges = reducedEdges(modules)
        val depth = mutableMapOf<String, Int>()
        fun depthOf(name: String): Int = depth.getOrPut(name) {
            byName.getValue(name).dependsOn.filter { it in byName }.maxOfOrNull { depthOf(it) + 1 } ?: 0
        }
        modules.forEach { depthOf(it.name) }
        val top = depth.values.max()

        val layers = List(top + 1) { mutableListOf<Slot>() }
        modules.forEach { layers[depth.getValue(it.name)] += Slot.Node(it) }
        edges.forEachIndexed { index, edge ->
            for (layer in depth.getValue(edge.to) + 1 until depth.getValue(edge.from)) layers[layer] += Slot.Lane(index)
        }

        // What each slot points to in the layer just below it: a module's box, or the next lane of a long arrow.
        fun below(slot: Slot, layer: Int): List<Slot> {
            fun next(index: Int): Slot {
                val edge = edges[index]
                return if (depth.getValue(edge.to) == layer - 1) Slot.Node(byName.getValue(edge.to)) else Slot.Lane(index)
            }
            return when (slot) {
                is Slot.Node -> edges.indices.filter { edges[it].from == slot.module.name }.map(::next)
                is Slot.Lane -> listOf(next(slot.edge))
            }
        }

        val authored = modules.withIndex().associate { (index, module) -> module.name to index }
        fun tieBreak(slot: Slot) = when (slot) {
            is Slot.Node -> authored.getValue(slot.module.name).toDouble()
            is Slot.Lane -> authored.getValue(edges[slot.edge].from) + 0.5
        }
        // 0..1 across its layer. A long arrow's lanes are equal in every layer, so a lane's entry is overwritten as
        // the layers go up; that is safe because each layer only reads the one below, already final.
        val position = mutableMapOf<Slot, Double>()
        val ordered = layers.mapIndexed { layer, slots ->
            val sorted = if (layer == 0) {
                slots
            } else {
                slots.sortedWith(
                    compareBy<Slot> { slot ->
                        below(slot, layer).mapNotNull { position[it] }.takeIf { it.isNotEmpty() }?.average() ?: 0.5
                    }.thenBy(::tieBreak),
                )
            }
            sorted.forEachIndexed { index, slot -> position[slot] = (index + 0.5) / sorted.size }
            sorted
        }
        return Plan(ordered, edges)
    }
}
