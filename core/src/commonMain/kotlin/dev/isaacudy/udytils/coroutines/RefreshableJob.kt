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

    override suspend fun awaitRefresh() {
        return performRefresh().await()
    }

    private suspend fun performRefresh(): Deferred<Unit> {
        return jobManager.async(refreshIdentifier, block = {
            jobManager.cancel(autoRefreshIdentifier)
            action()
            jobManager.launchReplacing(autoRefreshIdentifier) {
                val autoRefreshDuration = autoRefreshDuration ?: return@launchReplacing
                delay(autoRefreshDuration.inWholeMilliseconds)
                performRefresh()
            }
        })
    }
}
