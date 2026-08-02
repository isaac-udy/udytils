package dev.isaacudy.udytils.urpc.client.rest

/**
 * Thrown when a urpc call cannot be expressed as — or reconstructed from — the REST route it is
 * mapped to.
 *
 * This always signals a mismatch between the route table and the contract (a path placeholder
 * naming a field the request doesn't have, a nested object spilling into a query string, a
 * response body the contract's serializer can't read), never a failure reported *by* the API.
 * API-reported failures come back through the configured
 * [error decoder][RestUrpcConfig.errorDecoder] instead, which by default throws
 * [ServiceException][dev.isaacudy.udytils.urpc.ServiceException] exactly as the native urpc
 * client does.
 *
 * The distinction matters when handling errors at a call site: a [RestUrpcException] is a bug in
 * the mapping and will happen on every call, so it should be fixed rather than retried.
 */
class RestUrpcException internal constructor(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
