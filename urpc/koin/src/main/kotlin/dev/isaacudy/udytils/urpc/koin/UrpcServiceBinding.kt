package dev.isaacudy.udytils.urpc.koin

import dev.isaacudy.udytils.urpc.UrpcService
import org.koin.core.definition.KoinDefinition
import org.koin.dsl.ScopeDSL
import org.koin.dsl.bind

/**
 * Registers a generated `XServiceUrpcBinding` as a [UrpcService] in the current scope.
 *
 * Pass the binding's constructor reference (`::XServiceUrpcBinding`); the service implementation it
 * wraps is resolved lazily from the scope (so a call instantiates only the service it targets).
 *
 * Use this instead of `scoped<UrpcService> { XServiceUrpcBinding { get() } }`: that form gives every
 * binding in a scope the same Koin definition key (type `UrpcService`), so registering more than one
 * makes them override each other and `getAll<UrpcService>()` returns only one. This helper binds each
 * under its own concrete type, bound to [UrpcService], so several coexist in one scope.
 *
 * ```
 * scope<UrpcCall> {
 *     scopedOf(::ChatServiceImpl) bind ChatService::class
 *     urpcService(::ChatServiceUrpcBinding)
 * }
 * ```
 */
inline fun <reified Service : Any, reified Binding : UrpcService> ScopeDSL.urpcService(
    crossinline binding: (impl: () -> Service) -> Binding,
): KoinDefinition<Binding> {
    val definition = scoped { binding { get<Service>() } }
    definition.bind(UrpcService::class) // secondary type so getAll<UrpcService>() finds it
    return definition
}

/**
 * Chained form of [urpcService]: registers the generated `XServiceUrpcBinding` as a [UrpcService] in
 * the same scope as this definition, then returns this definition so the call reads as "bind the
 * impl, and expose it over urpc". The service implementation the binding wraps is resolved lazily.
 *
 * ```
 * scope<UrpcCall> {
 *     scopedOf(::ChatServiceImpl)
 *         .bind(ChatService::class)
 *         .bindService(::ChatServiceUrpcBinding)
 * }
 * ```
 */
inline fun <T, reified Service : Any, reified Binding : UrpcService> KoinDefinition<T>.bindService(
    crossinline binding: (impl: () -> Service) -> Binding,
): KoinDefinition<T> {
    // The binding is a distinct definition (not a member of this impl's definition), so register it
    // on the same module + scope qualifier this definition belongs to.
    module.scope(factory.beanDefinition.scopeQualifier) {
        scoped { binding { get<Service>() } }.bind(UrpcService::class)
    }
    return this
}
