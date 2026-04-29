package dev.isaacudy.udytils.urpc

/**
 * Marks a Kotlin interface as an urpc service definition. A service interface declares
 * its functions using ordinary Kotlin signatures:
 *  - `suspend fun foo(req: Req): Res` — unary HTTP request/response
 *  - `fun foo(req: Req): Flow<Res>`   — server-streaming WebSocket (planned)
 *  - `fun foo(reqs: Flow<Req>): Flow<Res>` — bidirectional WebSocket (planned)
 *
 * The [name] becomes the prefix of the wire identity of every function on the
 * interface (e.g. `"chat"` + function `sendMessage` produces wire name `"chat.sendMessage"`).
 * If [name] is left empty, the interface's simple name (lower-cased first character)
 * is used.
 *
 * The KSP processor (`:urpc:urpc-processor`) emits the matching client implementation
 * and server routing function alongside the annotated interface.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class UrpcService(val name: String = "")

/**
 * Overrides the default wire name of a single service function. Use this when you
 * need to rename a function in source without breaking already-deployed clients.
 *
 * ```
 * @UrpcService("chat")
 * interface ChatService {
 *     @UrpcWireName("send")        // wire name stays "chat.send" forever
 *     suspend fun sendMessage(req: ...): ...
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class UrpcWireName(val name: String)
