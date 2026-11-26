package sample

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

fun interface FlowOfGreetings {
    fun invoke(): Flow<String>
}

class GreetingSource {
    fun observe(): Flow<String> = flow {
        for (text in listOf("a", "b", "c")) {
            yield()
            emit(text)
        }
    }

    fun current(): StateFlow<String> = MutableStateFlow("now")
}

class Formatter {
    fun format(text: String): String = text.uppercase()
}

class GreetingFlowRepository(private val source: GreetingSource, private val formatter: Formatter) {
    val flowOfGreetings = FlowOfGreetings {
        source.observe().map { formatter.format(it) }.map { "$it!" }
    }
}

class Recorder {
    val received = mutableListOf<String>()

    fun record(text: String) {
        received += text
    }
}

class GreetingListModel(
    private val scope: CoroutineScope,
    private val flowOfGreetings: FlowOfGreetings,
    private val recorder: Recorder,
) {
    fun onStart(): Job = scope.launch { flowOfGreetings.invoke().collect { recorder.record(it) } }

    suspend fun firstGreeting(): String = flowOfGreetings.invoke().first()

    suspend fun currentGreeting(source: GreetingSource): String = source.current().first()
}

open class Loader(val scope: CoroutineScope, val label: String) {
    val state = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun load() {
        scope.launch {
            kotlinx.coroutines.delay(10)
            state.value = "loaded"
        }
    }
}

class SpecialLoader(scope: CoroutineScope) : Loader(scope, "special")

class RowStore {
    suspend fun list(): String {
        kotlinx.coroutines.yield()
        return "rows"
    }
}

class ConflatedRows(private val store: RowStore) {
    fun observe(): Flow<String> = flow {
        kotlinx.coroutines.yield()
        emit(Unit)
    }.conflate().map { store.list() }
}

class RowReader(private val rows: ConflatedRows) {
    suspend fun read(): String = rows.observe().first()
}
