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
