package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A cache that converts cold [Flow] instances into shared flows, keyed by [Key].
 *
 * Use [get] to retrieve a cached flow or create a new one using a provider block. The provider's
 * cold flow is collected in the provided [scope], and values are broadcast to all subscribers
 * via a [MutableSharedFlow] with replay of 1.
 *
 * Cache entries are automatically removed when:
 * - All subscribers have cancelled their collection (subscriber count reaches zero) and the
 *   [subscriptionTimeout] followed by the [replayTimeout] have elapsed
 * - Any subscriber encounters a non-cancellation error during collection
 *
 * [subscriptionTimeout] sets how long the upstream flow continues to be collected after all
 * subscribers leave. If a new subscriber arrives within this timeout, they will receive values
 * from the still-active upstream without interruption. Once the subscription timeout elapses,
 * the upstream collection is stopped.
 *
 * [replayTimeout] sets how long the last emitted value is retained in the replay cache after
 * the upstream has been stopped. If a new subscriber arrives within this timeout, they will
 * immediately receive the retained value before the upstream flow is restarted. Once the replay
 * timeout elapses, the replay cache is reset and the cache entry is removed.
 *
 * Set either timeout to [Duration.ZERO] to disable it, or to [Duration.INFINITE] to retain
 * indefinitely.
 *
 * When an entry is removed, its upstream collection coroutine is cancelled.
 */
class FlowCache<Key, T>(
    private val scope: CoroutineScope,
    private val subscriptionTimeout: Duration,
    private val replayTimeout: Duration,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Key, CacheEntry<T>>()

    private class CacheEntry<T>(
        val id: String,
        val sharedFlow: SharedFlow<Result<T>>,
    )

    /**
     * Returns a [Flow] backed by a cached shared flow for the given [key].
     * If no cached entry exists, the [flow] provider is called to create a cold flow, which is
     * then collected in the [scope] and broadcast to all subscribers.
     *
     * If the entry exists but the upstream has been stopped (due to all previous subscribers
     * leaving), the [flow] provider is called again to restart the upstream. Any retained value
     * from the previous collection is emitted immediately before fresh values arrive.
     *
     * Each collection is tracked as an active subscriber. When the last subscriber finishes
     * (via cancellation or completion), the [subscriptionTimeout] begins. If it elapses without
     * a new subscriber, the upstream is stopped and the [replayTimeout] begins. If the replay
     * timeout also elapses, the entry is removed from the cache.
     */
    fun get(key: Key, flow: () -> Flow<T>): Flow<T> = getOrCreate(key, flow)

    @OptIn(ExperimentalUuidApi::class)
    private fun getOrCreate(key: Key, provider: () -> Flow<T>): Flow<T> {
        if (!scope.isActive) throw IllegalStateException("FlowCache's Scope is not active")
        return flow {
            if (!scope.isActive) throw IllegalStateException("FlowCache's Scope is not active")

            val cacheEntry = mutex.withLock {
                val currentEntry = cache[key].let { cacheEntry ->
                    if (cacheEntry == null) return@let null
                    val exception =
                        cacheEntry.sharedFlow.replayCache.lastOrNull()?.exceptionOrNull()
                    if (exception != null) return@let null
                    return@let cacheEntry
                }
                if (currentEntry != null) return@withLock currentEntry
                val cacheEntryId = Uuid.random().toString()
                suspend fun removeFromCache() {
                    mutex.withLock {
                        val cacheEntry = cache[key]
                        if (cacheEntry?.id == cacheEntryId) {
                            cache.remove(key)
                        }
                    }
                }

                val sharingStartedDelegate = SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = subscriptionTimeout.inWholeMilliseconds,
                    replayExpirationMillis = replayTimeout.inWholeMilliseconds,
                )
                val sharingStarted = SharingStarted { subscriptionCount ->
                    sharingStartedDelegate
                        .command(subscriptionCount)
                        .onEach {
                            when (it) {
                                SharingCommand.STOP_AND_RESET_REPLAY_CACHE -> removeFromCache()
                                else -> { /* no op */
                                }
                            }
                        }
                }
                val cacheEntry = CacheEntry(
                    id = cacheEntryId,
                    sharedFlow = provider()
                        .map {
                            Result.success(it)
                        }
                        .catch {
                            removeFromCache()
                            emit(Result.failure(it))
                        }
                        .shareIn(
                            scope = scope,
                            replay = 1,
                            started = sharingStarted,
                        )
                )
                cache[key] = cacheEntry
                return@withLock cacheEntry
            }
            emitAll(
                cacheEntry
                    .sharedFlow
                    .map { it.getOrThrow() }
            )
        }
    }
}
