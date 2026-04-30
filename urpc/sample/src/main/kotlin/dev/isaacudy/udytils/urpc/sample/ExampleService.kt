package dev.isaacudy.udytils.urpc.sample

import dev.isaacudy.udytils.urpc.UrpcService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@UrpcService("example")
interface ExampleService {
    suspend fun sayHello(request: SayHelloRequest): SayHelloResponse
    suspend fun ping(): PongResponse

    fun countdown(request: CountdownRequest): Flow<CountdownTick>

    fun echoStream(requests: Flow<EchoMessage>): Flow<EchoMessage>

    // Streams a response type whose payload has a top-level `error` field, to
    // confirm the wire envelope keeps user data and framework errors separate.
    fun ambiguousStream(request: AmbiguousStreamRequest): Flow<AmbiguousResponse>

    // Streams a response and then throws — exercises the typed error envelope
    // path on the server side.
    fun failingStream(request: FailingStreamRequest): Flow<EchoMessage>
}

@Serializable
data class SayHelloRequest(val name: String)

@Serializable
data class SayHelloResponse(val greeting: String)

@Serializable
data class PongResponse(val message: String)

@Serializable
data class CountdownRequest(val from: Int)

@Serializable
data class CountdownTick(val remaining: Int)

@Serializable
data class EchoMessage(val text: String)

@Serializable
data class AmbiguousStreamRequest(val count: Int)

/**
 * Has a top-level `error` field — the kind of payload that would have been
 * misinterpreted as an error envelope under the pre-envelope wire format.
 */
@Serializable
data class AmbiguousResponse(
    val error: ErrorDetail?,
    val data: String,
)

@Serializable
data class ErrorDetail(val code: String, val description: String)

@Serializable
data class FailingStreamRequest(val emitBeforeFailing: Int)
