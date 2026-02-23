package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cache that converts cold [Flow] instances into shared flows, keyed by [Key].
 *
 * Use [get] to retrieve a cached flow or create a new one using a provider block. The provider's
 * cold flow is collected in the provided [scope], and values are broadcast to all subscribers
 * via a [MutableSharedFlow] with replay of 1.
 *
 * Cache entries are automatically removed when:
 * - All subscribers have cancelled their collection (subscriber count reaches zero)
 * - Any subscriber encounters a non-cancellation error during collection
 *
 * When an entry is removed, its upstream collection coroutine is cancelled.
 */
class FlowCache<Key, T>(
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Key, CacheEntry<T>>()

    private class CacheEntry<T>(
        val sharedFlow: MutableSharedFlow<Result<T>>,
        val job: Job,
        var subscriberCount: Int = 0,
    )

    /**
     * Returns a [Flow] backed by a cached shared flow for the given [key].
     * If no cached entry exists, the [flow] provider is called to create a cold flow, which is
     * then collected in the [scope] and broadcast to all subscribers.
     *
     * Each collection is tracked as an active subscriber. When the last subscriber finishes
     * (via cancellation or completion), the cache entry and its upstream collection are cleaned up.
     * If a non-cancellation exception occurs in the upstream flow, the cache entry is immediately
     * removed so that subsequent calls to [get] will create a fresh flow.
     */
    fun get(key: Key, flow: () -> Flow<T>): Flow<T> = getOrCreate(key, flow)

    private fun getOrCreate(key: Key, provider: () -> Flow<T>): Flow<T> = flow {
        val entry = mutex.withLock {
            cache.getOrPut(key) {
                val sharedFlow = MutableSharedFlow<Result<T>>(
                    replay = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
                val job = scope.launch {
                    try {
                        provider().collect { value ->
                            sharedFlow.emit(Result.success(value))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        sharedFlow.emit(Result.failure(e))
                    }
                }
                CacheEntry(sharedFlow = sharedFlow, job = job)
            }.also {
                it.subscriberCount++
            }
        }

        try {
            entry.sharedFlow.collect { result ->
                emit(result.getOrThrow())
            }
        } catch (e: Throwable) {
            if (e !is CancellationException) {
                mutex.withLock {
                    if (cache[key] === entry) {
                        cache.remove(key)
                        entry.job.cancel()
                    }
                }
            }
            throw e
        } finally {
            mutex.withLock {
                entry.subscriberCount--
                if (entry.subscriberCount <= 0 && cache[key] === entry) {
                    cache.remove(key)
                    entry.job.cancel()
                }
            }
        }
    }
}
