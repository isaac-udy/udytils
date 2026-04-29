package dev.isaacudy.udytils.urpc.sample

import dev.isaacudy.udytils.urpc.UrpcService
import kotlinx.serialization.Serializable

@UrpcService("example")
interface ExampleService {
    suspend fun sayHello(request: SayHelloRequest): SayHelloResponse
    suspend fun ping(): PongResponse
}

@Serializable
data class SayHelloRequest(val name: String)

@Serializable
data class SayHelloResponse(val greeting: String)

@Serializable
data class PongResponse(val message: String)
