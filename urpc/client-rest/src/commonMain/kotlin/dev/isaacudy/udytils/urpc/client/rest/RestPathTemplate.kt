package dev.isaacudy.udytils.urpc.client.rest

import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * A parsed `"/users/{id}/orders/{orderId}"` path, split once at configuration time so that a
 * malformed template fails while the route table is being built rather than on the first call
 * that happens to use it.
 */
internal class RestPathTemplate private constructor(
    val raw: String,
    private val parts: List<Part>,
    val placeholders: Set<String>,
) {

    /**
     * Renders the path, substituting each placeholder with the matching field of [fields], and
     * removes every substituted field so the caller can spill what's left into a query string or
     * request body.
     *
     * A placeholder may appear more than once; the field is read for each occurrence and removed
     * once, at the end.
     */
    fun render(fields: MutableMap<String, JsonElement>, route: String, wireName: String): String {
        val rendered = buildString {
            parts.forEach { part ->
                when (part) {
                    is Part.Literal -> append(part.text)
                    is Part.Placeholder -> append(substitute(part.field, fields, route, wireName))
                }
            }
        }
        placeholders.forEach { fields.remove(it) }
        return rendered
    }

    private fun substitute(
        field: String,
        fields: Map<String, JsonElement>,
        route: String,
        wireName: String,
    ): String {
        val value = fields[field] ?: throw RestUrpcException(
            "REST route '$route' for urpc call '$wireName' fills '{$field}' from the request " +
                "field '$field', which the encoded request does not contain. " +
                "Encoded fields: ${fields.keys.sorted()}.",
        )
        if (value is JsonNull) throw RestUrpcException(
            "REST route '$route' for urpc call '$wireName' fills '{$field}' from the request " +
                "field '$field', which is null. A path placeholder needs a value on every call — " +
                "make the field non-nullable, or map this call with `custom { }`.",
        )
        if (value !is JsonPrimitive) throw RestUrpcException(
            "REST route '$route' for urpc call '$wireName' fills '{$field}' from the request " +
                "field '$field', which encodes to a JSON ${value.describeKind()} rather than a " +
                "primitive. Only strings, numbers and booleans can appear in a path — map this " +
                "call with `custom { }` if the URL needs something the request doesn't carry flat.",
        )
        return value.content.encodeURLPathPart()
    }

    sealed class Part {
        class Literal(val text: String) : Part()
        class Placeholder(val field: String) : Part()
    }

    companion object {
        /**
         * @throws IllegalArgumentException if [path] has unbalanced or empty `{}` placeholders.
         *  Config-time by design: a route table is built once at startup, so a typo should fail
         *  there and not on whichever call first exercises the route.
         */
        fun parse(path: String): RestPathTemplate {
            val normalised = if (path.startsWith("/")) path else "/$path"
            val parts = mutableListOf<Part>()
            val placeholders = mutableSetOf<String>()
            val literal = StringBuilder()
            var index = 0
            while (index < normalised.length) {
                when (val char = normalised[index]) {
                    '{' -> {
                        val close = normalised.indexOf('}', startIndex = index + 1)
                        require(close >= 0) {
                            "REST path template '$path' has an unclosed '{' at index $index."
                        }
                        val field = normalised.substring(index + 1, close)
                        require(field.isNotBlank()) {
                            "REST path template '$path' has an empty placeholder at index $index. " +
                                "A placeholder names the request field that fills it, e.g. '/users/{id}'."
                        }
                        require('{' !in field) {
                            "REST path template '$path' has a nested '{' inside the placeholder " +
                                "starting at index $index."
                        }
                        if (literal.isNotEmpty()) {
                            parts += Part.Literal(literal.toString())
                            literal.clear()
                        }
                        parts += Part.Placeholder(field)
                        placeholders += field
                        index = close + 1
                    }

                    '}' -> throw IllegalArgumentException(
                        "REST path template '$path' has a '}' at index $index with no matching '{'.",
                    )

                    else -> {
                        literal.append(char)
                        index++
                    }
                }
            }
            if (literal.isNotEmpty()) parts += Part.Literal(literal.toString())
            return RestPathTemplate(raw = normalised, parts = parts, placeholders = placeholders)
        }
    }
}

/** Names the JSON shape of [this] for an error message, without leaking the value itself. */
internal fun JsonElement.describeKind(): String = when {
    this is JsonNull -> "null"
    this is JsonPrimitive -> "primitive"
    this is JsonArray -> "array"
    else -> "object"
}
