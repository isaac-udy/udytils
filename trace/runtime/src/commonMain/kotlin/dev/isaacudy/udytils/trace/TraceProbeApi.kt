package dev.isaacudy.udytils.trace

/** Marks the declarations that the trace compiler plugin calls from instrumented code. */
@RequiresOptIn("Called by code that the trace compiler plugin generates.")
@Retention(AnnotationRetention.BINARY)
annotation class TraceProbeApi

/** Excludes a function, or every function of a class or file, from instrumentation. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class NotTraced
