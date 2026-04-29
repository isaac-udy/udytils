package dev.isaacudy.udytils.urpc.server

// NOTE: there is intentionally NO `fun <T : Any> Route.urpc(impl: T, type: KClass<T>, ...)`
// fallback — see the matching note in `UrpcClientFactory.kt`. KSP emits a per-service
// `urpc(impl: ServiceX, type: KClass<ServiceX>, ...)` extension in the service's package;
// users `import` it from there. A generic fallback would be picked over the generated
// overload whenever it was imported (Kotlin prefers explicit imports over same-package
// implicit scope for extension functions), silently routing every call into the
// fallback's `error(...)`. Better to fail at compile time with `unresolved reference: urpc`.
