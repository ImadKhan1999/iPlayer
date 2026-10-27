package com.imadlabs.iplayer

import com.imadlabs.iplayer.playback.QueueItem
import com.imadlabs.iplayer.playback.QueueModel
import com.imadlabs.iplayer.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueModelTest {
    private fun items(n: Int) = (0 until n).map { QueueItem("file:///$it.mp3", "t$it", isVideo = false) }

    @Test fun nextStopsAtEndWithoutRepeat() {
        val q = QueueModel().apply { set(items(3), 2) }
        assertNull(q.nextIndex(auto = true))
    }

    @Test fun repeatAllWraps() {
        val q = QueueModel().apply { set(items(3), 2); repeat = RepeatMode.All }
        assertEquals(0, q.nextIndex(auto = true))
    }

    @Test fun repeatOneRepeatsOnlyWhenAutomatic() {
        val q = QueueModel().apply { set(items(3), 1); repeat = RepeatMode.One }
        assertEquals(1, q.nextIndex(auto = true))
        assertEquals(2, q.nextIndex(auto = false))
    }

    @Test fun previousAtStart() {
        val q = QueueModel().apply { set(items(3), 0) }
        assertNull(q.previousIndex())
        q.repeat = RepeatMode.All
        assertEquals(2, q.previousIndex())
    }

    @Test fun shuffleVisitsEveryItemOnceStartingWithCurrent() {
        val q = QueueModel(Random(42)).apply { set(items(10), 4); setShuffle(true) }
        assertEquals(4, q.shuffleOrder.first())
        assertEquals((0 until 10).toList(), q.shuffleOrder.sorted())
        val visited = mutableListOf(q.index)
        while (true) { val n = q.nextIndex(auto = true) ?: break; q.jumpTo(n); visited += n }
        assertEquals(10, visited.size)
        assertEquals(10, visited.toSet().size)
    }

    @Test fun addNextInsertsAfterCurrent() {
        val q = QueueModel().apply { set(items(3), 0) }
        val extra = QueueItem("file:///x.mp3", "x", isVideo = false)
        q.addNext(listOf(extra))
        assertEquals("x", q.items[1].title)
        assertEquals(1, q.nextIndex(auto = true))
    }

    @Test fun addNextWithShuffleKeepsOrderValid() {
        val q = QueueModel(Random(1)).apply { set(items(5), 2); setShuffle(true) }
        q.addNext(listOf(QueueItem("file:///x.mp3", "x", isVideo = false)))
        assertEquals((0 until 6).toList(), q.shuffleOrder.sorted())
        assertEquals("x", q.items[q.nextIndex(auto = true)!!].title)
    }

    @Test fun removeBeforeCurrentShiftsIndex() {
        val q = QueueModel().apply { set(items(4), 2) }
        val wasCurrent = q.removeAt(0)
        assertTrue(!wasCurrent)
        assertEquals(1, q.index)
        assertEquals("t2", q.current?.title)
    }

    @Test fun removeCurrentLastItemClampsIndex() {
        val q = QueueModel().apply { set(items(2), 1) }
        assertTrue(q.removeAt(1))
        assertEquals(0, q.index)
        q.removeAt(0)
        assertEquals(-1, q.index)
        assertNull(q.current)
    }

    @Test fun sameFileTwiceHasDistinctKeys() {
        val a = QueueItem("file:///a.mp3", "a"); val b = QueueItem("file:///a.mp3", "a")
        assertTrue(a.key != b.key)
    }
}
