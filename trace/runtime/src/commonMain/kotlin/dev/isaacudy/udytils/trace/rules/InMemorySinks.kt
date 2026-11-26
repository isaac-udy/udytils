package dev.isaacudy.udytils.trace.rules

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Stores every analytics event in the order the rules record them, for tests. */
class InMemoryAnalyticsSink<E> : AnalyticsSink<E> {
    val events: StateFlow<List<E>>
        field = MutableStateFlow(emptyList())

    override fun record(event: E, context: Map<String, String>) = events.update { it + event }
}

class RecordedTiming(val name: String, val durationNanos: Long, val tags: Map<String, String>)

/** Stores every timing in the order the rules record them, for tests. */
class InMemoryMetricsSink : MetricsSink {
    val timings: StateFlow<List<RecordedTiming>>
        field = MutableStateFlow(emptyList())

    override fun recordTiming(name: String, durationNanos: Long, tags: Map<String, String>) =
        timings.update { it + RecordedTiming(name, durationNanos, tags) }
}
