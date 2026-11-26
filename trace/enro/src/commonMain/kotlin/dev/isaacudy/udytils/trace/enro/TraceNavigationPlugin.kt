@file:OptIn(ExperimentalAtomicApi::class, InternalSerializationApi::class)

package dev.isaacudy.udytils.trace.enro

import androidx.lifecycle.ViewModel
import dev.enro.EnroController
import dev.enro.NavigationHandle
import dev.enro.NavigationKey
import dev.enro.getNavigationHandle
import dev.enro.plugin.NavigationPlugin
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.SpanKind
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.rules.Selector
import dev.isaacudy.udytils.trace.rules.visit
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializerOrNull
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass

/**
 * Records a [SpanKind.Visit] span for each Enro destination, from the destination's first open to
 * its close, named by [visitName] of the destination's key. Each span that a call of a ViewModel
 * starts links to the visit of the ViewModel's destination. [onScreen] selects the visits in rules.
 *
 * Register one instance with the navigation module: `createNavigationModule { plugin(TraceNavigationPlugin()) }`.
 */
class TraceNavigationPlugin : NavigationPlugin() {
    // Enro calls the navigation hooks on the main thread; visitOf reads the map from any thread.
    private val visits = AtomicReference(emptyMap<String, Span>())
    private var links: AutoCloseable? = null

    override fun onAttached(controller: EnroController) {
        links = Trace.linkRoots(::visitOf)
    }

    override fun onDetached(controller: EnroController) {
        links?.close()
        links = null
    }

    override fun onOpened(navigationHandle: NavigationHandle<*>) = open(navigationHandle)

    // Enro does not call onOpened for a destination restored from saved state.
    override fun onActive(navigationHandle: NavigationHandle<*>) = open(navigationHandle)

    override fun onClosed(navigationHandle: NavigationHandle<*>) {
        val id = navigationHandle.instance.id
        val visit = visits.load()[id] ?: return
        visits.store(visits.load() - id)
        Trace.endVisit(visit)
    }

    /** The open visit of the destination whose ViewModel is [receiver]; null for any other receiver. */
    fun visitOf(receiver: Any): Span? {
        if (receiver !is ViewModel) return null
        val handle = runCatching { receiver.getNavigationHandle() }.getOrNull() ?: return null
        return visits.load()[handle.instance.id]
    }

    private fun open(navigationHandle: NavigationHandle<*>) {
        val id = navigationHandle.instance.id
        if (id in visits.load()) return
        val key = navigationHandle.instance.key
        val visit = Trace.startVisit(visitName(key), receiver = key) ?: return
        visits.store(visits.load() + (id to visit))
    }
}

/** The serial name of [key]'s class, which [onScreen] selects by; the class's simple name for a key without a serializer. */
fun visitName(key: NavigationKey): String = visitName(key::class)

private fun visitName(keyType: KClass<out NavigationKey>): String =
    keyType.serializerOrNull()?.descriptor?.serialName ?: keyType.simpleName ?: "NavigationKey"

/** The visits of the destinations whose key is a [K], such as `onScreen(HomeKey::class)`; a rule's instance is the destination's key. */
@Suppress("UNCHECKED_CAST")
fun <K : NavigationKey> onScreen(keyType: KClass<K>): Selector<K> = visit(visitName(keyType)) as Selector<K>
