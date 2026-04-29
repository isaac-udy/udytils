package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.ServiceDescriptor
import dev.isaacudy.udytils.urpc.UrpcLogger
import dev.isaacudy.udytils.urpc.UrpcUnaryHandler
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * Registers a unary HTTP POST route for the given [descriptor], delegating to
 * [handler]. Generated server code calls into this from `_installUrpc(...)`.
 */
fun <Req, Res> Route.installUnary(
    descriptor: ServiceDescriptor<Req, Res>,
    errorMapper: ServiceErrorMapper = ServiceErrorMapper.Default,
    logger: UrpcLogger = UrpcLogger.NoOp,
    handler: UrpcUnaryHandler<Req, Res>,
) {
    post("/services/${descriptor.name}") {
        try {
            @Suppress("UNCHECKED_CAST")
            val request: Req = if (descriptor.isUnitRequest) {
                Unit as Req
            } else {
                serviceFunctionJson.decodeFromString(descriptor.requestSerializer, call.receive<String>())
            }
            val response = handler(request)
            if (descriptor.isUnitResponse) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    serviceFunctionJson.encodeToString(descriptor.responseSerializer, response),
                )
            }
        } catch (t: Throwable) {
            handleServiceError(call, t, errorMapper, logger)
        }
    }
}
