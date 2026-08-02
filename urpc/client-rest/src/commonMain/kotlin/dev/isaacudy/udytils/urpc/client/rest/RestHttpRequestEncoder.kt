package dev.isaacudy.udytils.urpc.client.rest

import dev.isaacudy.udytils.urpc.ServiceDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a mapped route resolved to for one call, before any header or retry handling. */
internal class RestHttpRequest(
    val path: String,
    val query: List<Pair<String, String>>,
    val body: String?,
)

/**
 * Marshals a typed urpc request into a REST request, following the route's shape: path
 * placeholders first, then whatever is left goes to the query string (GET/DELETE) or the JSON
 * body (POST/PUT/PATCH).
 *
 * Encoding is pure and deterministic, so a 401 retry can reuse the result and only re-run the
 * interceptor chain.
 */
internal fun <Req, Res> encodeRestRequest(
    descriptor: ServiceDescriptor<Req, Res>,
    request: Req,
    route: RestRoute.Http,
    json: Json,
): RestHttpRequest {
    val routeDescription = route.toString()
    if (descriptor.isUnitRequest) {
        if (route.template.placeholders.isNotEmpty()) throw RestUrpcException(
            "REST route '$routeDescription' for urpc call '${descriptor.name}' has path " +
                "placeholders ${route.template.placeholders.sorted()}, but the call takes no " +
                "request — there is nothing to fill them from. Give the route a literal path, or " +
                "map the call with `custom { }`.",
        )
        return RestHttpRequest(path = route.template.raw, query = emptyList(), body = null)
    }

    val encoded = json.encodeToJsonElement(descriptor.requestSerializer, request)
    val fields = (encoded as? JsonObject)?.toMutableMap() ?: throw RestUrpcException(
        "urpc call '${descriptor.name}' encodes its request to a JSON ${encoded.describeKind()} " +
            "rather than an object, so it has no fields to map onto REST route " +
            "'$routeDescription'. Map this call with `custom { }`.",
    )
    val path = route.template.render(fields, routeDescription, descriptor.name)
    return if (route.sendsBody) {
        RestHttpRequest(
            path = path,
            query = emptyList(),
            body = json.encodeToString(JsonObject.serializer(), JsonObject(fields)),
        )
    } else {
        RestHttpRequest(
            path = path,
            query = spillToQuery(fields, routeDescription, descriptor.name),
            body = null,
        )
    }
}

/**
 * Request fields a `GET`/`DELETE` route didn't put in its path, as query parameters.
 *
 * Primitives become one parameter each and arrays of primitives repeat the parameter name, which
 * is what conventional REST APIs expect. Nulls — top level or inside an array — are omitted
 * rather than sent as the literal `"null"`; an absent parameter is how REST spells "not
 * supplied", and the receiving contract's default fills in on the other side.
 */
private fun spillToQuery(
    fields: Map<String, JsonElement>,
    route: String,
    wireName: String,
): List<Pair<String, String>> = fields.flatMap { (name, value) ->
    when {
        value is JsonNull -> emptyList()
        value is JsonPrimitive -> listOf(name to value.content)
        value is JsonArray -> value.mapNotNull { element ->
            when {
                element is JsonNull -> null
                element is JsonPrimitive -> name to element.content
                else -> throw RestUrpcException(
                    "REST route '$route' for urpc call '$wireName' would send the request field " +
                        "'$name' as a repeated query parameter, but it contains a JSON " +
                        "${element.describeKind()}. Only arrays of primitives can be spelled in a " +
                        "query string — map this call with `custom { }`, or give the route a " +
                        "method that carries a body.",
                )
            }
        }

        else -> throw RestUrpcException(
            "REST route '$route' for urpc call '$wireName' would send the request field '$name' " +
                "in the query string, but it encodes to a JSON ${value.describeKind()}. Only " +
                "primitives and arrays of primitives can be spelled in a query string — map this " +
                "call with `custom { }`, or give the route a method that carries a body.",
        )
    }
}
