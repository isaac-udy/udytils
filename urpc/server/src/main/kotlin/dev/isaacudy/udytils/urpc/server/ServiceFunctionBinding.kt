package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.BidirectionalStreamingServiceFunction
import dev.isaacudy.udytils.urpc.ServiceFunction
import dev.isaacudy.udytils.urpc.StreamingServiceFunction
import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.WebSocketServerSession

/**
 * Server-side binding for a [ServiceFunction]. The runtime route handler resolves
 * a binding by [name] and delegates to [handle].
 */
interface ServiceFunctionBinding<Func : ServiceFunction<*, *>> {
    val name: String
    suspend fun handle(call: ApplicationCall)
}

/**
 * Server-side binding for a [StreamingServiceFunction]. The runtime websocket route
 * handler resolves a binding by [name] and delegates to [handle].
 */
interface StreamingServiceFunctionBinding<Func : StreamingServiceFunction<*, *>> {
    val name: String
    suspend fun handle(socketSession: WebSocketServerSession)
}

/**
 * Server-side binding for a [BidirectionalStreamingServiceFunction].
 */
interface BidirectionalStreamingServiceFunctionBinding<Func : BidirectionalStreamingServiceFunction<*, *>> {
    val name: String
    suspend fun handle(socketSession: WebSocketServerSession)
}
