package dev.isaacudy.udytils.urpc.client.rest

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement

/**
 * Builder for the routes of one urpc service — the block passed to
 * [RestUrpcConfig.service].
 *
 * [prefix] is the service's urpc wire-name prefix (the value of its `@Urpc(...)` annotation), so
 * `"getUser"` inside `service("userService")` maps the wire name `"userService.getUser"`. Routes
 * are declared with the [via] infix against one of the method builders:
 *
 * ```
 * service("userService") {
 *     headers("X-Api-Key" to apiKey)
 *     "getUser"    via get("/users/{id}")
 *     "listUsers"  via get("/users")
 *     "createUser" via post("/users")
 *     "search"     via post("/search") { response { it.jsonObject.getValue("data") } }
 * }
 * ```
 */
class RestServiceConfig internal constructor(
    val prefix: String,
) {

    internal val staticHeaders = mutableMapOf<String, String>()
    internal val declaredRoutes = mutableListOf<Pair<String, RestRoute>>()

    /**
     * Headers sent on every route in this service — API keys, tenant ids, API-version pins.
     *
     * Additive across calls, so several [headers] calls in one block accumulate. Where a header
     * name is also written into the call context by an
     * [interceptor][RestUrpcConfig.interceptors], the interceptor wins: interceptors carry the
     * per-call, freshly-computed value (an auth token, a trace id), and a static header is only
     * ever a default.
     */
    fun headers(vararg headers: Pair<String, String>) {
        staticHeaders += headers
    }

    /**
     * Maps the urpc function named by the receiver — `"getUser"`, resolving to the wire name
     * `"$prefix.getUser"` — to [route].
     *
     * Declaring the same function twice fails when the route table is built.
     */
    infix fun String.via(route: RestRoute) {
        require(isNotBlank()) { "A route in service '$prefix' was declared against a blank function name." }
        declaredRoutes += this to route
    }

    /**
     * `GET [path]`. Request fields not consumed by the path template spill into the query string.
     *
     * @param path a path relative to the client's `baseUrl`, optionally containing `{field}`
     *  placeholders naming request fields, e.g. `"/users/{id}/orders"`.
     */
    fun get(path: String, configure: RestRouteConfig.() -> Unit = {}): RestRoute =
        http(HttpMethod.Get, path, sendsBody = false, configure = configure)

    /**
     * `DELETE [path]`. Request fields not consumed by the path template spill into the query
     * string, matching REST conventions where a delete carries no body.
     */
    fun delete(path: String, configure: RestRouteConfig.() -> Unit = {}): RestRoute =
        http(HttpMethod.Delete, path, sendsBody = false, configure = configure)

    /**
     * `POST [path]`. Request fields not consumed by the path template become the JSON body; if
     * the path has no placeholders, the body is the whole encoded request.
     */
    fun post(path: String, configure: RestRouteConfig.() -> Unit = {}): RestRoute =
        http(HttpMethod.Post, path, sendsBody = true, configure = configure)

    /** `PUT [path]`; body rules as for [post]. */
    fun put(path: String, configure: RestRouteConfig.() -> Unit = {}): RestRoute =
        http(HttpMethod.Put, path, sendsBody = true, configure = configure)

    /** `PATCH [path]`; body rules as for [post]. */
    fun patch(path: String, configure: RestRouteConfig.() -> Unit = {}): RestRoute =
        http(HttpMethod.Patch, path, sendsBody = true, configure = configure)

    /**
     * A hand-written handler for endpoints the path/query/body rules can't express — multipart
     * uploads, pagination that has to be followed, two calls behind one urpc function.
     *
     * Both type arguments must be given explicitly and must match the urpc function's request
     * and response types; there is nothing at the declaration site for the compiler to infer
     * them from, and the route table is typed only by wire name:
     *
     * ```
     * "exportReport" via custom<ExportRequest, ExportResponse> { request -> … }
     * ```
     *
     * Inside the route table the handler is stored erased and its argument is cast back on
     * dispatch, so a type argument that doesn't match the contract fails as a `ClassCastException`
     * on the first call rather than at compile time — a test that makes the call once catches it.
     *
     * @see RestCallScope for what the handler can reach.
     */
    fun <Req, Res> custom(handler: suspend RestCallScope.(Req) -> Res): RestRoute {
        val erased: suspend RestCallScope.(Any?) -> Any? = { request ->
            @Suppress("UNCHECKED_CAST")
            handler(request as Req)
        }
        return RestRoute.Custom(erased)
    }

    private fun http(
        method: HttpMethod,
        path: String,
        sendsBody: Boolean,
        configure: RestRouteConfig.() -> Unit,
    ): RestRoute {
        val routeConfig = RestRouteConfig().apply(configure)
        return RestRoute.Http(
            method = method,
            template = RestPathTemplate.parse(path),
            sendsBody = sendsBody,
            responseTransform = routeConfig.responseTransform,
        )
    }
}

/** Per-route options — the optional trailing block on [RestServiceConfig.get] and friends. */
class RestRouteConfig internal constructor() {

    internal var responseTransform: ((JsonElement) -> JsonElement)? = null

    /**
     * Reshapes the response body before it is handed to the contract's serializer — the usual fix
     * for an API that wraps its payload in an envelope the urpc contract doesn't model.
     *
     * ```
     * "search" via post("/search") { response { it.jsonObject.getValue("data") } }
     * ```
     *
     * Runs only on a 2xx response with a non-`Unit` return type; error bodies go to the
     * [error decoder][RestUrpcConfig.errorDecoder] instead. Calling [response] twice replaces the
     * previous transform.
     */
    fun response(transform: (JsonElement) -> JsonElement) {
        responseTransform = transform
    }
}
