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

class RecordedTiming<M>(val metric: M, val durationNanos: Long)

/** Stores every timing in the order the rules record them, for tests. */
class InMemoryMetricsSink<M> : MetricsSink<M> {
    val timings: StateFlow<List<RecordedTiming<M>>>
        field = MutableStateFlow(emptyList())

    override fun recordTiming(metric: M, durationNanos: Long) =
        timings.update { it + RecordedTiming(metric, durationNanos) }
}
