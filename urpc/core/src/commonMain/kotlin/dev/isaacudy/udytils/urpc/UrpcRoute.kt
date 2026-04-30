package dev.isaacudy.udytils.urpc

/**
 * Transport-agnostic interface that a urpc server implementation provides for
 * registering service handlers.
 *
 * Generated `${ServiceName}.install(impl, type)` extensions (emitted by
 * `:urpc:urpc-processor`) call into [installUnary] / [installStreaming] /
 * [installBidirectional] — so the generated code only has to depend on
 * `:urpc:urpc-core`, not on Ktor.
 *
 * Obtain an instance inside a Ktor `routing { ... }` block via
 * `urpc(rootPath = "...") { /* this: UrpcRoute */ ... }` from `:urpc:urpc-server`.
 */
interface UrpcRoute {
    fun <Req, Res> installUnary(
        descriptor: ServiceDescriptor<Req, Res>,
        handler: UrpcUnaryHandler<Req, Res>,
    )

    fun <Req, Res> installStreaming(
        descriptor: StreamingServiceDescriptor<Req, Res>,
        handler: UrpcStreamingHandler<Req, Res>,
    )

    fun <Req, Res> installBidirectional(
        descriptor: BidirectionalServiceDescriptor<Req, Res>,
        handler: UrpcBidirectionalHandler<Req, Res>,
    )
}
