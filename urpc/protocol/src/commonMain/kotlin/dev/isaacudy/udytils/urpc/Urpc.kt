package dev.isaacudy.udytils.urpc

/**
 * Marks a Kotlin interface as a urpc service contract. The KSP processor
 * (`:urpc:processor`) emits a matching `${Service}UrpcBinding`, descriptors,
 * and client implementation alongside the annotated interface.
 *
 * Each function on the interface must match one of three shapes:
 *  - `suspend fun foo(req: Req): Res`        — unary HTTP request/response
 *  - `fun foo(req: Req): Flow<Res>`          — server-streaming WebSocket
 *  - `fun foo(reqs: Flow<Req>): Flow<Res>`   — bidirectional WebSocket
 *
 * The [name] becomes the prefix of the wire identity of every function on the
 * interface (e.g. `"chat"` + function `sendMessage` produces wire name
 * `"chat.sendMessage"`). If [name] is left empty, the interface's simple name
 * with the first character lowercased is used.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Urpc(val name: String = "")

/**
 * Overrides the default wire name of a single service function. Use this when
 * you need to rename a function in source without breaking already-deployed
 * clients.
 *
 * ```
 * @Urpc("chat")
 * interface ChatService {
 *     @UrpcWireName("send")        // wire name stays "chat.send" forever
 *     suspend fun sendMessage(req: ...): ...
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class UrpcWireName(val name: String)
