package dev.isaacudy.udytils.trace.otlp

import dev.isaacudy.udytils.trace.SiteFlags
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.remoteParent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.engine.mock.toByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class OtlpSpanExporterTest {
    @Test
    fun flushPostsTheQueuedSpansAsOtlpJson() = runTest {
        val bodies = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            bodies += request.body.toByteArray().decodeToString()
            respondOk()
        })
        val exporter = OtlpSpanExporter("http://collector/v1/traces", "sample", client, backgroundScope)
        val span = remoteParent("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")!!.span

        exporter.onEnd(span)
        exporter.flush()

        val resource = Json.parseToJsonElement(bodies.single()).jsonObject["resourceSpans"]!!.jsonArray.single().jsonObject
        val exported = resource["scopeSpans"]!!.jsonArray.single().jsonObject["spans"]!!.jsonArray.single().jsonObject
        assertEquals("0af7651916cd43dd8448eb211c80319c", exported["traceId"]!!.jsonPrimitive.content)
        assertEquals("b7ad6b7169203331", exported["spanId"]!!.jsonPrimitive.content)
        exporter.close()
    }

    @Test
    fun aVisitIsExportedWhenItStartsAndAClosedChildWhenItEnds() = runTest {
        val bodies = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            bodies += request.body.toByteArray().decodeToString()
            respondOk()
        })
        val exporter = OtlpSpanExporter("http://collector/v1/traces", "sample", client, backgroundScope) {
            if (it.isVisit) SiteFlags.RECORD else 0
        }
        val registration = Trace.addSink(exporter)
        val visit = Trace.startVisit("sample.Home")!!

        exporter.flush()
        val open = spansOf(bodies.last()).single()
        Trace.endVisit(visit)
        exporter.flush()
        val closed = spansOf(bodies.last()).single()

        assertEquals("Home (visit)", open["name"]!!.jsonPrimitive.content)
        assertEquals("Home (visit) (closed)", closed["name"]!!.jsonPrimitive.content)
        assertEquals(open["spanId"], closed["parentSpanId"])
        registration.close()
        exporter.close()
    }

    private fun spansOf(body: String) = Json.parseToJsonElement(body).jsonObject["resourceSpans"]!!.jsonArray.single().jsonObject["scopeSpans"]!!
        .jsonArray.single().jsonObject["spans"]!!.jsonArray.map { it.jsonObject }

}
