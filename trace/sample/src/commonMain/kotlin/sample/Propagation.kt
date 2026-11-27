package sample

import kotlinx.coroutines.currentCoroutineContext
import dev.isaacudy.udytils.trace.spanOf
import dev.isaacudy.udytils.trace.Trace
import dev.isaacudy.udytils.trace.Span
import dev.isaacudy.udytils.trace.NotTraced
import dev.isaacudy.udytils.coroutines.JobManager
import dev.isaacudy.udytils.trace.declarationIdOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

fun interface LoadGreeting {
    suspend fun invoke(): String
}

class GreetingStore {
    suspend fun read(): String {
        yield()
        return "hello"
    }
}

class LoadingRepository(private val store: GreetingStore) {
    val loadGreeting = LoadGreeting { store.read() }
}

val loadGreetingDeclarationId: Long = declarationIdOf(LoadGreeting::invoke)

class GreetingModel(
    private val scope: CoroutineScope,
    private val loadGreeting: LoadGreeting,
    private val jobs: JobManager,
) {
    var greeting: String? = null
        private set

    fun onGreetClicked(): Job = scope.launch { greeting = loadGreeting.invoke() }

    fun onGreetClickedThroughJobManager(): Job = jobs.launchReplacing("greet") { greeting = loadGreeting.invoke() }

    fun onClick(): Int = 1
}

class Waiter {
    val release = CompletableDeferred<Unit>()

    suspend fun waitForRelease() {
        release.await()
    }
}

class Builders(private val store: GreetingStore) {
    suspend fun viaCoroutineScope(): String = coroutineScope { store.read() }

    suspend fun viaWithContext(dispatcher: CoroutineDispatcher): String = withContext(dispatcher) { store.read() }

    suspend fun viaWithTimeout(): String = withTimeout(10_000) { store.read() }

    suspend fun viaFlowOn(dispatcher: CoroutineDispatcher): String = flow { emit(store.read()) }.flowOn(dispatcher).first()
}

suspend fun valueInCancelledCoroutine(): Int = 42

class SpanReader {
    @NotTraced
    suspend fun read(): Span? {
        yield()
        return Trace.spanOf(currentCoroutineContext())
    }
}

class SpanReaderCaller(private val reader: SpanReader) {
    suspend fun call(): Span? = reader.read()
}
