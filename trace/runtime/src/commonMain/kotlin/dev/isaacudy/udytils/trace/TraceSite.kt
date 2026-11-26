package dev.isaacudy.udytils.trace

import kotlin.concurrent.Volatile

/**
 * One instrumented function body. The compiler plugin creates one instance per body as a static
 * field and passes it to [traceEnter].
 *
 * [declarationId] identifies the declaration that rules reference: for an override or a `fun
 * interface` lambda, the overridden interface method. [siteId] identifies this body.
 */
class TraceSite @TraceProbeApi constructor(
    val siteId: Long,
    val declarationId: Long,
    val name: String,
) {
    @Volatile
    var flags: Int = Trace.register(this)
        internal set

    override fun toString(): String = name
}

object SiteFlags {
    /** The site records a span for each call. */
    const val RECORD: Int = 1

    /** The site captures argument values into its spans. */
    const val ARGS: Int = 2

    /** A call to the site starts a new trace. */
    const val ROOT: Int = 4
}
