package dev.yowkey.portfolio.ui.image

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads values by key on [dispatcher] and keeps the [capacity] most recently used. Lazy rows dispose their
 * images when they scroll away; this keeps scrolling back from re-reading and re-decoding them.
 *
 * Two callers asking for the same key at once may both load it; the second result simply replaces the first.
 * That is cheaper than tracking in-flight loads for a handful of images.
 */
class LruLoader<K : Any, V : Any>(
    private val capacity: Int,
    private val dispatcher: CoroutineDispatcher,
    private val load: suspend (K) -> V,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val mutex = Mutex()
    private val entries = LinkedHashMap<K, V>() // iteration order = least recently used first

    suspend fun get(key: K): V {
        mutex.withLock {
            entries.remove(key)?.let { cached ->
                entries[key] = cached
                return cached
            }
        }
        val value = withContext(dispatcher) { load(key) }
        mutex.withLock {
            entries[key] = value
            while (entries.size > capacity) entries.remove(entries.keys.first())
        }
        return value
    }

    suspend fun cachedKeys(): List<K> = mutex.withLock { entries.keys.toList() }
}
