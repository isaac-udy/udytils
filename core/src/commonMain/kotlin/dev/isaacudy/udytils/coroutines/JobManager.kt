package dev.isaacudy.udytils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Manages a collection of active coroutine jobs with options to join existing jobs
 * or cancel and replace them with new jobs.
 */
class JobManager(
    @PublishedApi internal val coroutineScope: CoroutineScope,
) {

    @PublishedApi internal val activeJobs = mutableMapOf<Any, Pair<KType, Job>>()
    @PublishedApi internal val mutex = Mutex()

    /**
     * Strategy for handling existing jobs when launching a new job with the same key
     */
    enum class JobStrategy {
        /** Join the existing job if it exists, otherwise launch a new one */
        JOIN,
        /** Cancel the existing job and replace it with the new one */
        REPLACE,
    }

    inline fun launch(
        identifier: Any = object {}::class,
        crossinline block: suspend CoroutineScope.() -> Unit
    ): Job {
        return coroutineScope.launch {
            val result = async(
                identifier = identifier,
                strategy = JobStrategy.JOIN,
                block = block
            )
            result.await()
        }
    }

    inline fun launchReplacing(
        identifier: Any = object {}::class,
        crossinline block: suspend CoroutineScope.() -> Unit
    ): Job {
        return coroutineScope.launch {
            async(
                identifier = identifier,
                strategy = JobStrategy.REPLACE,
                block = block
            ).await()
        }
    }

    suspend inline fun <reified T> async(
        identifier: Any = object {}::class,
        crossinline block: suspend CoroutineScope.() -> T
    ): Deferred<T> {
        return async(
            identifier = identifier,
            strategy = JobStrategy.JOIN,
            block = block
        )
    }

    suspend inline fun <reified T> asyncReplacing(
        identifier: Any = object {}::class,
        crossinline block: suspend CoroutineScope.() -> T
    ): Deferred<T> {
        return async(
            identifier = identifier,
            strategy = JobStrategy.REPLACE,
            block = block
        )
    }

    suspend inline fun <reified T> async(
        identifier: Any,
        strategy: JobStrategy,
        crossinline block: suspend CoroutineScope.() -> T
    ): Deferred<T> {
        val type = typeOf<T>()
        val job = mutex.withLock {
            val toJoin = when (strategy) {
                JobStrategy.JOIN -> activeJobs[identifier]
                JobStrategy.REPLACE -> {
                    activeJobs.remove(identifier)?.second?.cancel()
                    null
                }
            }

            if (toJoin != null && toJoin.second.isActive) {
                val (jobType, existingJob) = toJoin
                require(jobType == type) {
                    "Job type mismatch: expected $type, but found $jobType"
                }
                @Suppress("UNCHECKED_CAST")
                return@withLock existingJob as Deferred<T>
            }

            return@withLock coroutineScope.async {
                try {
                    block()
                } finally {
                    ensureActive()
                    activeJobs.remove(identifier)
                }
            }.also { launchedJob ->
                activeJobs[identifier] = type to launchedJob
            }
        }
        return job
    }

    suspend fun cancel(
        identifier: Any,
    ) {
        mutex.withLock {
            activeJobs[identifier]?.second?.cancel()
            activeJobs.remove(identifier)
        }
    }
}
