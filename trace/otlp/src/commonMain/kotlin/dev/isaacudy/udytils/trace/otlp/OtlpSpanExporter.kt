package dev.isaacudy.udytils.trace.otlp

import dev.isaacudy.udytils.trace.Outcome
import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.SpanKind
import dev.isaacudy.udytils.trace.SpanSink
import dev.isaacudy.udytils.trace.TraceSite
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import dev.isaacudy.udytils.trace.Trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Exports the spans of the sites that [select] selects to an OpenTelemetry collector, such as
 * Jaeger, as OTLP/HTTP JSON at [endpoint] (for example `http://localhost:4318/v1/traces`).
 *
 * [onEnd] only queues the span; a coroutine in [scope] posts the queued spans every
 * [flushInterval]. A failed post drops its batch.
 *
 * A [SpanKind.Collect] or [SpanKind.Visit] span can stay open for the life of a screen, so
 * [onStart] queues the span under the span's own IDs with a duration of 1 ns, and [onEnd] queues
 * a child span named "(closed)" that covers the span's whole duration and outcome. A collector
 * therefore stores the span, and the spans whose parent the span is, before the span ends, and
 * stores each span ID once.
 */
class OtlpSpanExporter(
    private val endpoint: String,
    private val serviceName: String,
    private val client: HttpClient,
    scope: CoroutineScope,
    private val flushInterval: Duration = 1.seconds,
    private val select: (TraceSite) -> Int = { SiteFlags.RECORD },
) : SpanSink, AutoCloseable {
    private val queue = Channel<JsonObject>(Channel.UNLIMITED)
    private val job: Job = scope.launch {
        while (isActive) {
            delay(flushInterval)
            flush()
        }
    }

    override fun flagsFor(site: TraceSite): Int = select(site)

    override fun onStart(span: Span) {
        if (span.kind == SpanKind.Collect || span.kind == SpanKind.Visit) {
            queue.trySend(otlpSpan(span, Clock.System.now().toEpochNanos(), Record.Open))
        }
    }

    override fun onEnd(span: Span) {
        val record = if (span.kind == SpanKind.Collect || span.kind == SpanKind.Visit) Record.Closed else Record.Whole
        queue.trySend(otlpSpan(span, Clock.System.now().toEpochNanos(), record))
    }

    /** Posts every queued span in one request. */
    suspend fun flush() {
        val spans = generateSequence { queue.tryReceive().getOrNull() }.toList()
        if (spans.isEmpty()) return
        runCatching {
            client.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(request(spans).toString())
            }
        }
    }

    override fun close() {
        job.cancel()
        queue.close()
    }

    private fun request(spans: List<JsonObject>) = buildJsonObject {
        putJsonArray("resourceSpans") {
            addJsonObject {
                putJsonObject("resource") { put("attributes", attributes(mapOf("service.name" to serviceName))) }
                putJsonArray("scopeSpans") {
                    addJsonObject {
                        putJsonObject("scope") { put("name", "dev.isaacudy.udytils.trace") }
                        put("spans", JsonArray(spans))
                    }
                }
            }
        }
    }
}

internal enum class Record {
    /** The span, from its start to [endNanos]. */
    Whole,

    /** The span under its own IDs, from [endNanos] for 1 ns, while the span runs. */
    Open,

    /** A child of the span, from the span's start to [endNanos]. */
    Closed,
}

internal fun otlpSpan(span: Span, endNanos: Long, record: Record = Record.Whole): JsonObject = buildJsonObject {
    put("traceId", span.traceId.toString())
    if (record == Record.Closed) {
        put("spanId", Random.nextLong().hex())
        put("parentSpanId", span.spanId.hex())
        put("name", "${spanName(span)} (closed)")
    } else {
        put("spanId", span.spanId.hex())
        span.parentSpanId?.let { put("parentSpanId", it.hex()) }
        put("name", spanName(span))
    }
    put("kind", 1)
    val open = record == Record.Open
    put("startTimeUnixNano", (if (open) endNanos else endNanos - span.durationNanos).toString())
    put("endTimeUnixNano", (if (open) endNanos + 1 else endNanos).toString())
    val outcome = span.outcome
    val values = buildMap {
        put("code.function", span.site.name)
        put("trace.kind", span.kind.name)
        put("trace.outcome", outcome.toString())
        span.attributes.forEach { (key, value) -> put(key, value.toString()) }
    }
    put("attributes", attributes(values))
    if (span.links.isNotEmpty()) {
        putJsonArray("links") {
            span.links.forEach { link ->
                addJsonObject {
                    put("traceId", link.traceId.toString())
                    put("spanId", link.spanId.hex())
                }
            }
        }
    }
    if (outcome is Outcome.Threw) {
        putJsonObject("status") {
            put("code", 2)
            put("message", outcome.error.toString())
        }
    }
}

private fun spanName(span: Span): String {
    val name = span.site.name.split('.').takeLast(2).joinToString(".")
    return when (span.kind) {
        SpanKind.Call -> name
        SpanKind.Collect -> "$name (collect)"
        SpanKind.FirstValue -> "$name (first value)"
        SpanKind.Visit -> "${span.site.name.substringAfterLast('.')} (visit)"
    }
}

private fun attributes(values: Map<String, String>) = buildJsonArray {
    values.forEach { (key, value) ->
        addJsonObject {
            put("key", key)
            putJsonObject("value") { put("stringValue", JsonPrimitive(value)) }
        }
    }
}

private fun Long.hex(): String = toULong().toString(16).padStart(16, '0')

private fun kotlin.time.Instant.toEpochNanos(): Long = epochSeconds * 1_000_000_000 + nanosecondsOfSecond

/**
 * Registers an [OtlpSpanExporter] for every traced site and returns the registration; closing the
 * registration stops the exporter and its coroutine.
 */
fun exportTraces(endpoint: String, serviceName: String, client: HttpClient): AutoCloseable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val exporter = OtlpSpanExporter(endpoint, serviceName, client, scope)
    val registration = Trace.addSink(exporter)
    return AutoCloseable {
        registration.close()
        exporter.close()
        scope.cancel()
    }
}
