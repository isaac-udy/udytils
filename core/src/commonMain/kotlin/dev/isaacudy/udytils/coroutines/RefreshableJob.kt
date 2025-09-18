package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi

interface RefreshableJob {
    fun autoRefresh(every: Duration) : RefreshableJob
    fun refresh(): Job

    suspend fun <T : Any> refreshAfter(
        action: suspend () -> T,
    ) : T

    suspend fun awaitRefresh()

    companion object {
        fun create(
            coroutineScope: CoroutineScope,
            action: suspend () -> Unit,
        ): RefreshableJob {
            return RefreshableJobImpl(coroutineScope, action)
        }
    }
}

class RefreshableJobImpl(
    private val coroutineScope: CoroutineScope,
    private val action: suspend () -> Unit,
) : RefreshableJob {

    @OptIn(ExperimentalUuidApi::class)
    private val refreshIdentifier = "refresh"
    @OptIn(ExperimentalUuidApi::class)
    private val autoRefreshIdentifier = "autoRefresh"

    private var autoRefreshDuration: Duration? = null
    private val jobManager = JobManager(coroutineScope)

    override fun autoRefresh(every: Duration): RefreshableJob {
        autoRefreshDuration = every
        refresh()
        return this
    }

    override fun refresh(): Job {
        return coroutineScope.launch {
            performRefresh().await()
        }
    }

    /**
     * Waits for any currently running refreshes to finish, executes the "action" block,
     * and then performs a refresh
     */
    override suspend fun <T : Any> refreshAfter(action: suspend () -> T): T {
        var result: T? = null
        performRefresh(
            beforeRefresh = {
                result = action()
            },
        ).await()
        return requireNotNull(result)
    }

    override suspend fun awaitRefresh() {
        return performRefresh().await()
    }

    private suspend fun performRefresh(
        beforeRefresh: (suspend () -> Unit)? = null,
    ): Deferred<Unit> {
        // If we've got a "beforeRefresh" action to perform,
        // we need to await the current refresh before we execute the next refresh,
        // which will execute the "beforeRefresh" action
        if (beforeRefresh != null) { jobManager.await(refreshIdentifier) }
        return jobManager.async(refreshIdentifier, block = {
            jobManager.cancel(autoRefreshIdentifier)
            if (beforeRefresh != null) beforeRefresh()
            action()
            jobManager.launchReplacing(autoRefreshIdentifier) {
                val autoRefreshDuration = autoRefreshDuration ?: return@launchReplacing
                delay(autoRefreshDuration.inWholeMilliseconds)
                performRefresh()
            }
        })
    }
}
