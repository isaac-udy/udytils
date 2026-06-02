package dev.isaacudy.udytils.urpc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * A [UrpcClientInterceptor] that attaches a bearer token (sourced from [tokens]) to each call's
 * metadata under [headerName] — the canonical way to do auth in urpc.
 *
 * When [gateStreaming] is true (the default), streaming and bidirectional calls **suspend** until
 * [tokens] emits a non-null value. Because the transport only registers a call (and only opens the
 * shared socket) once its interceptors complete, a logged-out client opens no streaming connection
 * — no reconnect storm — and resumes automatically once a token appears. Unary calls never gate
 * (so an unauthenticated call such as login still works); they attach the current token if present.
 *
 * [tokens] should be a hot flow with a current value (e.g. a `StateFlow<String?>`), since unary
 * calls read it with [Flow.first].
 */
fun bearerTokenInterceptor(
    tokens: Flow<String?>,
    gateStreaming: Boolean = true,
    headerName: String = "Authorization",
): UrpcClientInterceptor = UrpcClientInterceptor { context ->
    val token = if (gateStreaming && context.kind != UrpcCallKind.UNARY) {
        tokens.filterNotNull().first() // gate: suspend until authenticated
    } else {
        tokens.first()
    }
    if (token != null) context.metadata[headerName] = "Bearer $token"
}
