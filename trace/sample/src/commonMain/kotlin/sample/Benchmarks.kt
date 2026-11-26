package sample

import dev.isaacudy.udytils.trace.NotTraced
import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.SpanSink
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.TraceSite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeSource

class TracedWork {
    fun plain(n: Int): Int = n + 1
    suspend fun suspending(n: Int): Int = n + 1
    suspend fun calling(n: Int): Int = suspending(n) + 1
    fun values(count: Int): Flow<Int> = flow { for (i in 0 until count) emit(i) }
}

@NotTraced
class UntracedWork {
    fun plain(n: Int): Int = n + 1
    suspend fun suspending(n: Int): Int = n + 1
    suspend fun calling(n: Int): Int = suspending(n) + 1
    fun values(count: Int): Flow<Int> = flow { for (i in 0 until count) emit(i) }
}

/** Selects the benchmark's traced sites for recording and discards their spans. */
@NotTraced
private class RecordingSink : SpanSink {
    var ended = 0
    override fun flagsFor(site: TraceSite): Int = if (site.name.startsWith("sample.TracedWork")) SiteFlags.RECORD else 0
    override fun onEnd(span: Span) { ended++ }
}

@NotTraced
suspend fun runBenchmarks(calls: Int = 2_000_000, emissions: Int = 1_000_000): String {
    val traced = TracedWork()
    val untraced = UntracedWork()
    val lines = mutableListOf<String>()
    suspend fun round(label: String) {
        var sum = 0L
        val results = linkedMapOf<String, Double>()
        suspend fun time(name: String, count: Int, block: suspend () -> Unit) {
            val start = TimeSource.Monotonic.markNow()
            block()
            results[name] = start.elapsedNow().inWholeNanoseconds.toDouble() / count
        }
        time("plain traced", calls) { for (i in 0 until calls) sum += traced.plain(i) }
        time("plain untraced", calls) { for (i in 0 until calls) sum += untraced.plain(i) }
        time("suspend traced", calls) { for (i in 0 until calls) sum += traced.suspending(i) }
        time("suspend untraced", calls) { for (i in 0 until calls) sum += untraced.suspending(i) }
        time("suspend→suspend traced", calls) { for (i in 0 until calls) sum += traced.calling(i) }
        time("suspend→suspend untraced", calls) { for (i in 0 until calls) sum += untraced.calling(i) }
        time("flow emission traced", emissions) { traced.values(emissions).collect { sum += it } }
        time("flow emission untraced", emissions) { untraced.values(emissions).collect { sum += it } }
        if (sum == 42L) lines += "unreachable"
        lines += "$label: " + results.entries.joinToString { (k, v) -> "$k=${(v * 100).toLong() / 100.0}ns" }
    }
    repeat(3) { round("warmup ${it + 1} (disabled)") }
    round("disabled")
    val sink = RecordingSink()
    val registration = Trace.addSink(sink)
    try {
        repeat(2) { round("warmup ${it + 1} (recorded)") }
        round("recorded")
    } finally {
        registration.close()
    }
    return lines.filterNot { it.startsWith("warmup") }.joinToString("\n")
}
