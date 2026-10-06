package de.visualtasker.blockeditor.compose.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DropTraceRecorderTest {
    @Test
    fun eventsShareDropIdAndUseMonotonicDeltas() {
        val clock = FakeClock()
        val lines = mutableListOf<String>()
        val recorder = DropTraceRecorder(clock, DropTraceCpuClock { 17L }, lines::add)

        val id = recorder.begin("PRESENTATION_MOVE", 181)
        clock.advance(2_000_000L)
        recorder.mark(id, "T1_END_DRAG_ENTER")
        clock.advance(3_000_000L)
        recorder.mark(id, "T2_END_DRAG_RETURN")

        val snapshot = requireNotNull(recorder.snapshot(id))
        assertEquals(listOf(0L, 2_000_000L, 5_000_000L), snapshot.events.map { it.elapsedNanos })
        assertEquals(listOf(0L, 2_000_000L, 3_000_000L), snapshot.events.map { it.deltaNanos })
        assertTrue(lines.all { it.startsWith("DROP#$id ") })
    }

    @Test
    fun revisionCorrelatesComposeAndStableEventsWithoutDuplicates() {
        val clock = FakeClock(10_000_000L)
        val recorder = DropTraceRecorder(clock, DropTraceCpuClock { 0L }, DropTraceSink { })
        val id = recorder.begin("REPORTER_ATTACH", 181)
        recorder.bindRevision(id, 42L)
        clock.advance(4_000_000L)
        recorder.markRevision(42L, "T16_COMPOSE_OBSERVED_STATE")
        recorder.markRevision(42L, "T16_COMPOSE_OBSERVED_STATE")
        recorder.frame(42L, 30_000_000L)
        recorder.completeWithoutAnimation(42L)

        val snapshot = requireNotNull(recorder.snapshot(id))
        assertEquals(1, snapshot.events.count { it.phase == "T16_COMPOSE_OBSERVED_STATE" })
        assertEquals(20_000_000L, snapshot.largestFrameGapNanos)
        assertTrue(snapshot.stable)
        assertTrue(recorder.isStableRevision(42L))
    }

    @Test
    fun stableWaitsForAllPlacementAnimations() {
        val clock = FakeClock()
        val recorder = DropTraceRecorder(clock, DropTraceCpuClock { 0L }, DropTraceSink { })
        val id = recorder.begin("LINEAR_RECONNECT", 181)
        recorder.bindRevision(id, 9L)
        recorder.animationStarted(9L)
        recorder.animationStarted(9L)
        recorder.animationFinished(9L)
        assertTrue(requireNotNull(recorder.snapshot(id)).stable.not())

        recorder.animationFinished(9L)

        val snapshot = requireNotNull(recorder.snapshot(id))
        assertTrue(snapshot.stable)
        assertEquals(1, snapshot.events.count { it.phase == "T24_STABLE" })
    }

    private class FakeClock(initial: Long = 0L) : DropTraceClock {
        private var now = initial

        override fun elapsedRealtimeNanos(): Long = now

        fun advance(delta: Long) {
            now += delta
        }
    }
}
