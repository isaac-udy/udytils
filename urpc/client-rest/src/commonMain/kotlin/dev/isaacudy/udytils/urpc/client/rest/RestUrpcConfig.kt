package dev.isaacudy.udytils.urpc.client.rest

import dev.isaacudy.udytils.urpc.UrpcClientFactory
import dev.isaacudy.udytils.urpc.UrpcClientInterceptor
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

/**
 * The route table and per-client settings of a [restUrpcClient] — the receiver of its
 * `configure` block.
 *
 * Everything here is read once, when the client is constructed; the resulting table is immutable,
 * so a route can't appear or change while calls are in flight.
 */
class RestUrpcConfig internal constructor() {

    /**
     * [Json] used to encode requests and decode responses.
     *
     * Defaults to urpc's shared
     * [serviceFunctionJson][dev.isaacudy.udytils.urpc.serviceFunctionJson] (`ignoreUnknownKeys`,
     * `encodeDefaults`, `allowStructuredMapKeys`) so a payload is marshalled identically before
     * and after an endpoint migrates. Replace it when the REST API's naming or null handling
     * differs from the contract's — the common one is `encodeDefaults = false` for an API that
     * reads an absent field as "leave it alone".
     */
    var json: Json = serviceFunctionJson

    private val interceptorList = mutableListOf<UrpcClientInterceptor>()
    private var refresher: (suspend () -> Unit)? = null
    private var decoder: RestErrorDecoder? = null
    private val routes = LinkedHashMap<String, ConfiguredRestRoute>()
    private val prefixes = LinkedHashSet<String>()

    /**
     * Adds client interceptors, run in order before every call (including
     * [custom][RestServiceConfig.custom] routes) to populate per-call metadata, which becomes
     * HTTP request headers.
     *
     * Identical to the native client's chain — pass the same list to both and auth behaves the
     * same on either side of a migration. An interceptor that suspends gates the call, and the
     * chain is re-run before a [tokenRefresher]-triggered retry so the retry picks up the new
     * token.
     *
     * Additive: several calls accumulate.
     */
    fun interceptors(vararg interceptors: UrpcClientInterceptor) {
        interceptorList += interceptors
    }

    /** Adds client interceptors; behaves exactly like the `vararg` overload above. */
    fun interceptors(interceptors: List<UrpcClientInterceptor>) {
        interceptorList += interceptors
    }

    /**
     * Invoked when a mapped route returns 401; the call is then retried exactly once, re-running
     * the interceptor chain first.
     *
     * A second 401 is passed to the [errorDecoder] like any other failure — there is no loop. A
     * refresher that throws is swallowed, so the retry still happens and the eventual error is
     * the API's, not the refresher's. Without a refresher configured, a 401 goes straight to the
     * decoder.
     *
     * Custom routes are not retried: they own their exchange.
     */
    fun tokenRefresher(refresher: suspend () -> Unit) {
        this.refresher = refresher
    }

    /**
     * Overrides how non-2xx responses become exceptions. Defaults to [serviceErrorDecoder], which
     * matches the native urpc client.
     */
    fun errorDecoder(decoder: RestErrorDecoder) {
        this.decoder = decoder
    }

    /**
     * Declares routes for the urpc service whose wire-name prefix is [prefix] — the value of its
     * `@Urpc(...)` annotation.
     *
     * The same prefix may be opened more than once (to group routes by feature, say); the blocks
     * merge, but each block's [headers][RestServiceConfig.headers] apply only to the routes
     * declared in it.
     */
    fun service(prefix: String, configure: RestServiceConfig.() -> Unit) {
        require(prefix.isNotBlank()) { "A urpc service prefix must not be blank." }
        val service = RestServiceConfig(prefix).apply(configure)
        prefixes += prefix
        service.declaredRoutes.forEach { (function, route) ->
            val wireName = "$prefix.$function"
            val existing = routes.put(
                wireName,
                ConfiguredRestRoute(route, service.staticHeaders.toMap()),
            )
            require(existing == null) {
                "urpc call '$wireName' is mapped twice: to '${existing?.route}' and to '$route'. " +
                    "Each wire name may have at most one REST route."
            }
        }
    }

    internal fun build(
        httpClient: HttpClient,
        baseUrl: String,
        fallback: UrpcClientFactory?,
        logger: UrpcLogger,
    ): RestUrpcClientFactory = RestUrpcClientFactory(
        httpClient = httpClient,
        baseUrl = baseUrl,
        routes = routes.toMap(),
        servicePrefixes = prefixes.toSet(),
        json = json,
        interceptors = interceptorList.toList(),
        tokenRefresher = refresher,
        errorDecoder = decoder ?: serviceErrorDecoder(json),
        fallback = fallback,
        logger = logger,
    )
}

/** A route plus the service-level headers it inherited. */
internal class ConfiguredRestRoute(
    val route: RestRoute,
    val staticHeaders: Map<String, String>,
)
