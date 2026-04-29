package dev.isaacudy.udytils.urpc

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer

/**
 * Pure-data description of a single urpc function. Carries everything the client
 * caller and server binding need to serialise traffic on the wire — and nothing
 * else. Created by KSP-generated code; manually instantiated only in advanced
 * scenarios.
 */
class ServiceDescriptor<Req, Res>(
    val name: String,
    val requestSerializer: KSerializer<Req>,
    val responseSerializer: KSerializer<Res>,
    val isUnitRequest: Boolean,
    val isUnitResponse: Boolean,
)

// TODO(urpc): the streaming descriptors are wired up in core but the KSP processor
// does NOT yet generate code for streaming functions in the first iteration. Manual
// descriptor construction still works.
class StreamingServiceDescriptor<Req, Res>(
    val name: String,
    val requestSerializer: KSerializer<Req>,
    val responseSerializer: KSerializer<Res>,
    val isUnitRequest: Boolean,
)

class BidirectionalServiceDescriptor<Req, Res>(
    val name: String,
    val requestSerializer: KSerializer<Req>,
    val responseSerializer: KSerializer<Res>,
)

/** Internal handler shape — what server bindings call into. */
typealias UrpcUnaryHandler<Req, Res> = suspend (Req) -> Res
typealias UrpcStreamingHandler<Req, Res> = (Req) -> Flow<Res>
typealias UrpcBidirectionalHandler<Req, Res> = (Flow<Req>) -> Flow<Res>
