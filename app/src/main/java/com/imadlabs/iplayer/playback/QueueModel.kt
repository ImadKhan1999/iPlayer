package com.imadlabs.iplayer.playback

import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

enum class RepeatMode { Off, All, One }

data class QueueItem(
    val uri: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0,
    val isVideo: Boolean = true,
    /** Unique per queue entry, so the same file can appear twice. */
    val key: Long = keys.incrementAndGet(),
) {
    companion object { private val keys = AtomicLong() }
}

/**
 * Pure play-queue logic: order, shuffle order, repeat and navigation. No Android dependencies,
 * fully unit tested. Indices passed in and out always refer to [items] (the visible order).
 */
class QueueModel(private val random: Random = Random.Default) {
    var items: List<QueueItem> = emptyList(); private set
    var index: Int = -1; private set
    var shuffle: Boolean = false; private set
    var repeat: RepeatMode = RepeatMode.Off
    /** Play order as indices into [items] while shuffle is on. */
    var shuffleOrder: List<Int> = emptyList(); private set

    val current: QueueItem? get() = items.getOrNull(index)
    val isEmpty get() = items.isEmpty()

    fun set(newItems: List<QueueItem>, startIndex: Int = 0) {
        items = newItems
        index = if (newItems.isEmpty()) -1 else startIndex.coerceIn(0, newItems.lastIndex)
        rebuildShuffle()
    }

    fun restore(newItems: List<QueueItem>, startIndex: Int, shuffleOn: Boolean, order: List<Int>, repeatMode: RepeatMode) {
        items = newItems
        index = if (newItems.isEmpty()) -1 else startIndex.coerceIn(0, newItems.lastIndex)
        repeat = repeatMode
        shuffle = shuffleOn
        shuffleOrder = if (shuffleOn && order.sorted() == newItems.indices.toList()) order else emptyList()
        if (shuffleOn && shuffleOrder.isEmpty()) rebuildShuffle()
    }

    fun setShuffle(enabled: Boolean) {
        shuffle = enabled
        rebuildShuffle()
    }

    private fun rebuildShuffle() {
        if (!shuffle || items.isEmpty()) { shuffleOrder = emptyList(); return }
        val rest = items.indices.filter { it != index }.shuffled(random)
        shuffleOrder = if (index >= 0) listOf(index) + rest else rest
    }

    private fun order(): List<Int> = if (shuffle) shuffleOrder else items.indices.toList()

    /**
     * Index to play after the current item, or null when playback should stop.
     * [auto] is true when advancing because the current item ended (repeat-one applies then).
     */
    fun nextIndex(auto: Boolean): Int? {
        if (items.isEmpty()) return null
        if (auto && repeat == RepeatMode.One) return index
        val o = order()
        val pos = o.indexOf(index)
        return when {
            pos < 0 -> o.firstOrNull()
            pos + 1 < o.size -> o[pos + 1]
            repeat == RepeatMode.All || (!auto && repeat == RepeatMode.One) -> o.first()
            else -> null
        }
    }

    fun previousIndex(): Int? {
        if (items.isEmpty()) return null
        val o = order()
        val pos = o.indexOf(index)
        return when {
            pos > 0 -> o[pos - 1]
            repeat != RepeatMode.Off -> o.last()
            else -> null
        }
    }

    fun jumpTo(i: Int): Boolean {
        if (i !in items.indices) return false
        index = i
        return true
    }

    /** Inserts right after the current item (in play order). */
    fun addNext(newItems: List<QueueItem>) {
        if (newItems.isEmpty()) return
        if (items.isEmpty()) { set(newItems, 0); return }
        val insertAt = index + 1
        items = items.toMutableList().apply { addAll(insertAt, newItems) }
        if (shuffle) {
            val shifted = shuffleOrder.map { if (it >= insertAt) it + newItems.size else it }.toMutableList()
            val pos = shifted.indexOf(index) + 1
            shifted.addAll(pos, newItems.indices.map { insertAt + it })
            shuffleOrder = shifted
        }
    }

    fun addLast(newItems: List<QueueItem>) {
        if (newItems.isEmpty()) return
        if (items.isEmpty()) { set(newItems, 0); return }
        val start = items.size
        items = items + newItems
        if (shuffle) shuffleOrder = shuffleOrder + newItems.indices.map { start + it }.shuffled(random)
    }

    /** Removes the entry at [i]. Returns true when the removed entry was the current one. */
    fun removeAt(i: Int): Boolean {
        if (i !in items.indices) return false
        val wasCurrent = i == index
        items = items.toMutableList().apply { removeAt(i) }
        if (shuffle) shuffleOrder = shuffleOrder.filter { it != i }.map { if (it > i) it - 1 else it }
        index = when {
            items.isEmpty() -> -1
            i < index -> index - 1
            wasCurrent -> index.coerceAtMost(items.lastIndex)
            else -> index
        }
        return wasCurrent
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val list = items.toMutableList()
        list.add(to, list.removeAt(from))
        items = list
        fun remap(i: Int) = when {
            i == from -> to
            from < to && i in (from + 1)..to -> i - 1
            from > to && i in to until from -> i + 1
            else -> i
        }
        index = remap(index)
        if (shuffle) shuffleOrder = shuffleOrder.map(::remap)
    }

    fun clear() = set(emptyList())
}
