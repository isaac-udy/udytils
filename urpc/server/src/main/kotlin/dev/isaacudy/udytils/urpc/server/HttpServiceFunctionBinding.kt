package dev.isaacudy.udytils.urpc.server

import dev.isaacudy.udytils.urpc.ServiceFunction
import dev.isaacudy.udytils.urpc.serviceFunctionJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.serialization.KSerializer

class HttpServiceFunctionBinding<Func : ServiceFunction<Request, Response>, Request : Any, Response : Any>(
    override val name: String,
    private val requestSerializer: KSerializer<Request>,
    private val responseSerializer: KSerializer<Response>,
    private val isUnitRequest: Boolean,
    private val isUnitResponse: Boolean,
    private val errorMapper: ServiceErrorMapper,
    private val handler: suspend (Request) -> Response,
) : ServiceFunctionBinding<Func> {
    override suspend fun handle(call: ApplicationCall) {
        try {
            val request: Request = if (isUnitRequest) {
                @Suppress("UNCHECKED_CAST")
                Unit as Request
            } else {
                serviceFunctionJson.decodeFromString(requestSerializer, call.receive<String>())
            }
            val response = handler(request)
            if (isUnitResponse) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    serviceFunctionJson.encodeToString(responseSerializer, response),
                )
            }
        } catch (t: Throwable) {
            handleServiceError(call, t, errorMapper)
        }
    }
}
