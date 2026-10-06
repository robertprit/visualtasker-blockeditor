package de.visualtasker.blockeditor.compose.debug

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

fun interface DropTraceSink {
    fun emit(line: String)
}

fun interface DropTraceClock {
    fun elapsedRealtimeNanos(): Long
}

fun interface DropTraceCpuClock {
    fun currentThreadCpuTimeNanos(): Long
}

data class DropTraceEvent(
    val phase: String,
    val elapsedNanos: Long,
    val deltaNanos: Long,
    val cpuNanos: Long,
    val cpuDeltaNanos: Long,
    val threadName: String,
    val threadId: Long,
    val details: String,
)

data class DropTraceSnapshot(
    val id: Long,
    val type: String,
    val blockCount: Int,
    val documentVersion: Long?,
    val events: List<DropTraceEvent>,
    val largestFrameGapNanos: Long,
    val stable: Boolean,
)

/** Debug-only timeline recorder. It observes an existing drop without changing editor state. */
class DropTraceRecorder(
    private val clock: DropTraceClock,
    private val cpuClock: DropTraceCpuClock,
    private val sink: DropTraceSink,
) {
    private val nextId = AtomicLong(0L)
    private val sessions = ConcurrentHashMap<Long, Session>()
    private val revisionToTrace = ConcurrentHashMap<Long, Long>()
    private val activeTraceId = AtomicLong(NO_TRACE)

    fun begin(type: String, blockCount: Int, details: String = ""): Long {
        val id = nextId.incrementAndGet()
        val now = clock.elapsedRealtimeNanos()
        val session = Session(id, type, blockCount, now, cpuClock.currentThreadCpuTimeNanos())
        sessions[id] = session
        activeTraceId.set(id)
        mark(id, "T0_POINTER_UP", details)
        return id
    }

    fun bindRevision(traceId: Long, documentVersion: Long) {
        sessions[traceId]?.documentVersion = documentVersion
        revisionToTrace[documentVersion] = traceId
        mark(traceId, "REVISION_BOUND", "version=$documentVersion")
    }

    fun activeId(): Long? = activeTraceId.get().takeUnless { it == NO_TRACE }

    fun traceIdForRevision(documentVersion: Long): Long? = revisionToTrace[documentVersion]

    fun markActive(phase: String, details: String = "") {
        activeId()?.let { mark(it, phase, details) }
    }

    fun markRevision(documentVersion: Long, phase: String, details: String = "") {
        traceIdForRevision(documentVersion)?.let { mark(it, phase, details) }
    }

    fun mark(traceId: Long, phase: String, details: String = "") {
        val session = sessions[traceId] ?: return
        if (!session.markedPhases.add(phase)) return
        val now = clock.elapsedRealtimeNanos()
        val previous = session.lastEventNanos.getAndSet(now)
        val cpuNow = cpuClock.currentThreadCpuTimeNanos()
        val previousCpu = session.lastCpuNanos.getAndSet(cpuNow)
        val event = DropTraceEvent(
            phase = phase,
            elapsedNanos = now - session.startedNanos,
            deltaNanos = now - previous,
            cpuNanos = cpuNow,
            cpuDeltaNanos = (cpuNow - previousCpu).coerceAtLeast(0L),
            threadName = Thread.currentThread().name,
            threadId = Thread.currentThread().id,
            details = details,
        )
        session.events += event
        sink.emit(event.toLogLine(session))
        if (phase == "T24_STABLE") {
            session.stable = true
        }
    }

    fun frame(documentVersion: Long, frameTimeNanos: Long) {
        val traceId = traceIdForRevision(documentVersion) ?: return
        val session = sessions[traceId] ?: return
        val previous = session.lastFrameNanos.getAndSet(frameTimeNanos)
        val gap = if (previous == 0L) frameTimeNanos - session.startedNanos else frameTimeNanos - previous
        session.largestFrameGapNanos.accumulateAndGet(gap, ::maxOf)
        if (previous == 0L) {
            mark(traceId, "FIRST_FRAME_AFTER_POINTER_UP", "frameGapMs=${gap.toMillisText()}")
            mark(traceId, "T23_UI_INTERACTIVE_FRAME", "proxy=firstChoreographerFrame")
        } else if (gap >= LARGE_FRAME_GAP_NANOS) {
            val index = session.largeFrameGapCount.incrementAndGet()
            mark(traceId, "FRAME_GAP_$index", "gapMs=${gap.toMillisText()}")
        }
    }

    fun frameNow(documentVersion: Long) {
        frame(documentVersion, SystemClock.elapsedRealtimeNanos())
    }

    fun animationStarted(documentVersion: Long) {
        val traceId = traceIdForRevision(documentVersion) ?: return
        val session = sessions[traceId] ?: return
        session.activeAnimations.incrementAndGet()
        mark(traceId, "T22_PLACEMENT_ANIMATION_START")
    }

    fun animationFinished(documentVersion: Long) {
        val traceId = traceIdForRevision(documentVersion) ?: return
        val session = sessions[traceId] ?: return
        if (session.activeAnimations.decrementAndGet() <= 0) {
            mark(traceId, "PLACEMENT_ANIMATIONS_END")
            mark(traceId, "T24_STABLE", "reason=placementAnimationsComplete")
        }
    }

    fun completeWithoutAnimation(documentVersion: Long) {
        val traceId = traceIdForRevision(documentVersion) ?: return
        val session = sessions[traceId] ?: return
        if (session.activeAnimations.get() == 0) {
            mark(traceId, "T24_STABLE", "reason=firstDrawNoPlacementAnimation")
        }
    }

    fun snapshot(traceId: Long): DropTraceSnapshot? = sessions[traceId]?.let { session ->
        DropTraceSnapshot(
            id = session.id,
            type = session.type,
            blockCount = session.blockCount,
            documentVersion = session.documentVersion,
            events = session.events.toList(),
            largestFrameGapNanos = session.largestFrameGapNanos.get(),
            stable = session.stable,
        )
    }

    fun isStableRevision(documentVersion: Long): Boolean =
        traceIdForRevision(documentVersion)?.let { sessions[it]?.stable } == true

    private class Session(
        val id: Long,
        val type: String,
        val blockCount: Int,
        val startedNanos: Long,
        startedCpuNanos: Long,
    ) {
        @Volatile var documentVersion: Long? = null
        @Volatile var stable: Boolean = false
        val events = CopyOnWriteArrayList<DropTraceEvent>()
        val markedPhases = ConcurrentHashMap.newKeySet<String>()
        val lastEventNanos = AtomicLong(startedNanos)
        val lastCpuNanos = AtomicLong(startedCpuNanos)
        val lastFrameNanos = AtomicLong(0L)
        val largestFrameGapNanos = AtomicLong(0L)
        val largeFrameGapCount = AtomicInteger(0)
        val activeAnimations = AtomicInteger(0)
    }

    private fun DropTraceEvent.toLogLine(session: Session): String = buildString {
        append("DROP#").append(session.id)
        append(" type=").append(session.type)
        append(" blocks=").append(session.blockCount)
        append(" phase=").append(phase)
        append(" atMs=").append(elapsedNanos.toMillisText())
        append(" deltaMs=").append(deltaNanos.toMillisText())
        append(" cpuDeltaMs=").append(cpuDeltaNanos.toMillisText())
        append(" thread=").append(threadName).append('/').append(threadId)
        if (details.isNotBlank()) append(' ').append(details)
    }

    private companion object {
        const val NO_TRACE = -1L
        const val LARGE_FRAME_GAP_NANOS = 32_000_000L
    }
}

object BlockEditorDropTrace {
    @Volatile var enabled: Boolean = de.visualtasker.blockeditor.compose.BuildConfig.DEBUG

    private val recorder = DropTraceRecorder(
        clock = DropTraceClock(SystemClock::elapsedRealtimeNanos),
        cpuClock = DropTraceCpuClock(Debug::threadCpuTimeNanos),
        sink = DropTraceSink { Log.d(LOG_TAG, it) },
    )

    fun begin(type: String, blockCount: Int, details: String = ""): Long? =
        if (enabled) recorder.begin(type, blockCount, details) else null

    fun bindRevision(traceId: Long?, documentVersion: Long) {
        if (enabled && traceId != null) recorder.bindRevision(traceId, documentVersion)
    }

    fun activeId(): Long? = if (enabled) recorder.activeId() else null

    fun mark(traceId: Long?, phase: String, details: String = "") {
        if (enabled && traceId != null) recorder.mark(traceId, phase, details)
    }

    fun markActive(phase: String, details: String = "") {
        if (enabled) recorder.markActive(phase, details)
    }

    fun markRevision(documentVersion: Long, phase: String, details: String = "") {
        if (enabled) recorder.markRevision(documentVersion, phase, details)
    }

    fun frame(documentVersion: Long, frameTimeNanos: Long) {
        if (enabled) recorder.frame(documentVersion, frameTimeNanos)
    }

    fun frameNow(documentVersion: Long) {
        if (enabled) recorder.frameNow(documentVersion)
    }

    fun animationStarted(documentVersion: Long) {
        if (enabled) recorder.animationStarted(documentVersion)
    }

    fun animationFinished(documentVersion: Long) {
        if (enabled) recorder.animationFinished(documentVersion)
    }

    fun completeWithoutAnimation(documentVersion: Long) {
        if (enabled) recorder.completeWithoutAnimation(documentVersion)
    }

    fun isStableRevision(documentVersion: Long): Boolean =
        enabled && recorder.isStableRevision(documentVersion)
}

private fun Long.toMillisText(): String = "%.3f".format(this / 1_000_000.0)

private const val LOG_TAG = "VTWSS/DropTrace"
