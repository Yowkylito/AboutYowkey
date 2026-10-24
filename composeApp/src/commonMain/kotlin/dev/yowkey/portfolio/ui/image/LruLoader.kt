package dev.yowkey.portfolio.ui.image

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads values by key on [dispatcher] and keeps the most recently used within [capacity]. Lazy rows dispose their
 * images when they scroll away; this keeps scrolling back from re-reading and re-decoding them.
 *
 * [capacity] is a total of [weigh] over the entries. The default weight of 1 makes it a count; the image caches
 * weigh in decoded bytes, so a few big images and many small ones share one memory budget honestly. The entry just
 * loaded is always kept, even if it alone is over budget, since its caller is about to draw it.
 *
 * Two callers asking for the same key at once may both load it; the second result simply replaces the first.
 * That is cheaper than tracking in-flight loads for a handful of images.
 */
class LruLoader<K : Any, V : Any>(
    private val capacity: Long,
    private val dispatcher: CoroutineDispatcher,
    private val weigh: (V) -> Long = { 1L },
    private val load: suspend (K) -> V,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val mutex = Mutex()
    private val entries = LinkedHashMap<K, V>() // iteration order = least recently used first
    private var weight = 0L

    suspend fun get(key: K): V {
        mutex.withLock {
            entries.remove(key)?.let { cached ->
                entries[key] = cached
                return cached
            }
        }
        val value = withContext(dispatcher) { load(key) }
        mutex.withLock {
            entries.put(key, value)?.let { weight -= weigh(it) }
            weight += weigh(value)
            while (weight > capacity && entries.size > 1) {
                val oldest = entries.keys.first()
                weight -= weigh(entries.remove(oldest)!!)
            }
        }
        return value
    }

    /**
     * The value cached for [key], at once and as a use, without loading it; null when it is not cached, or when a
     * load is storing its result this instant (the caller then asks [get]). For a composable coming back on screen,
     * so it draws what the cache holds in its first frame rather than a placeholder for one.
     */
    fun peek(key: K): V? {
        if (!mutex.tryLock()) return null
        try {
            val cached = entries.remove(key) ?: return null
            entries[key] = cached
            return cached
        } finally {
            mutex.unlock()
        }
    }

    suspend fun cachedKeys(): List<K> = mutex.withLock { entries.keys.toList() }

    /** The summed [weigh] of what is cached now. */
    suspend fun cachedWeight(): Long = mutex.withLock { weight }
}
