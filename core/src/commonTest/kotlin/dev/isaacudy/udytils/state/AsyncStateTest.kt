package dev.isaacudy.udytils.state

import dev.isaacudy.udytils.error.PresentableException
import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.asAsyncState
import dev.isaacudy.udytils.state.errorOrNull
import dev.isaacudy.udytils.state.getOrNull
import dev.isaacudy.udytils.state.getOrThrow
import dev.isaacudy.udytils.state.isError
import dev.isaacudy.udytils.state.isIdle
import dev.isaacudy.udytils.state.isLoading
import dev.isaacudy.udytils.state.isSuccess
import dev.isaacudy.udytils.state.isTerminal
import dev.isaacudy.udytils.state.map
import dev.isaacudy.udytils.state.onError
import dev.isaacudy.udytils.state.onIdle
import dev.isaacudy.udytils.state.onLoading
import dev.isaacudy.udytils.state.onSuccess
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncStateTest {

    // region State Creation & Basic Properties
    @Test
    fun `Idle state should have correct properties`() {
        val idle1 = AsyncState.Idle<String>()

        assertEquals("AsyncState.Idle", idle1.toString())
        assertNotNull(idle1.hashCode())
    }

    @Test
    fun `Loading state should have correct properties`() {
        val loadingIndeterminate = AsyncState.Loading<String>()
        val loadingDeterminate = AsyncState.Loading<String>(progress = 0.5f)
        val loadingClamped = AsyncState.Loading<String>(progress = 1.5f)

        assertTrue(loadingIndeterminate.isIndeterminate)
        assertNull(loadingIndeterminate.progress)
        assertEquals("AsyncState.Loading", loadingIndeterminate.toString())

        assertFalse(loadingDeterminate.isIndeterminate)
        assertEquals(0.5f, loadingDeterminate.progress)
        assertEquals("AsyncState.Loading(progress=0.5)", loadingDeterminate.toString())

        assertEquals(1.0f, loadingClamped.progress) // Clamped between 0 and 1
    }

    @Test
    fun `Success state should hold data`() {
        val success = AsyncState.Success("Test Data")
        assertEquals("Test Data", success.data)
    }

    @Test
    fun `Error state should hold an error`() {
        val error = RuntimeException("Test Error")
        val errorState = AsyncState.Error<String>(error)
        assertEquals(error, errorState.error)
    }

    @Test
    fun `presentableError factory should create Error state with PresentableException`() {
        val errorState = AsyncState.presentableError<String>("Error Title", "Error Message")

        assertIs<PresentableException>(errorState.error)
        assertEquals("Error Title", errorState.error.errorMessage.title)
        assertEquals("Error Title", errorState.error.message)
        assertEquals("Error Message", errorState.error.errorMessage.message)
    }
    // endregion

    // region Conversion Extensions
    @Test
    fun `asAsyncState should convert data to Success`() {
        val data = "Success"
        val state = data.asAsyncState()
        assertIs<AsyncState.Success<String>>(state)
        assertEquals(data, state.data)
    }

    @Test
    fun `asAsyncState should convert Throwable to Error`() {
        val error = RuntimeException("Failure")
        val state: AsyncState<Any> = error.asAsyncState()
        assertIs<AsyncState.Error<String>>(state)
        assertEquals(error, state.error)
    }
    // endregion

    // region Getters
    @Test
    fun `getOrNull should return data on Success and null otherwise`() {
        assertEquals("data", AsyncState.Success("data").getOrNull())
        assertNull(AsyncState.Idle<String>().getOrNull())
        assertNull(AsyncState.Loading<String>().getOrNull())
        assertNull(AsyncState.Error<String>(RuntimeException()).getOrNull())
    }

    @Test
    fun `getOrThrow should return data on Success and throw otherwise`() {
        assertEquals("data", AsyncState.Success("data").getOrThrow())
        assertFailsWith<IllegalStateException> { AsyncState.Idle<String>().getOrThrow() }
        assertFailsWith<IllegalStateException> { AsyncState.Loading<String>().getOrThrow() }
        assertFailsWith<RuntimeException> {
            AsyncState.Error<String>(RuntimeException()).getOrThrow()
        }
    }

    @Test
    fun `flow getOrThrow should return data from first terminal state`() = runTest {
        val successFlow = flowOf(AsyncState.Loading(), AsyncState.Success("data"))
        assertEquals("data", successFlow.getOrThrow())

        val error = RuntimeException()
        val errorFlow = flowOf(AsyncState.Loading(), AsyncState.Error<String>(error))
        assertFailsWith<RuntimeException> { errorFlow.getOrThrow() }
    }

    @Test
    fun `errorOrNull should return error on Error and null otherwise`() {
        val error = RuntimeException()
        assertEquals(error, AsyncState.Error<String>(error).errorOrNull())
        assertNull(AsyncState.Success("data").errorOrNull())
        assertNull(AsyncState.Idle<String>().errorOrNull())
        assertNull(AsyncState.Loading<String>().errorOrNull())
    }
    // endregion

    // region State Checks

    @Test
    fun `isIdle should return true only for Idle state`() {
        assertTrue(AsyncState.Idle<Unit>().isIdle())
        assertFalse(AsyncState.Loading<Unit>().isIdle())
        assertFalse(AsyncState.Success(Unit).isIdle())
        assertFalse(AsyncState.Error<Unit>(RuntimeException()).isIdle())
    }

    @Test
    fun `isLoading should return true only for Loading state`() {
        assertFalse(AsyncState.Idle<Unit>().isLoading())
        assertTrue(AsyncState.Loading<Unit>().isLoading())
        assertFalse(AsyncState.Success(Unit).isLoading())
        assertFalse(AsyncState.Error<Unit>(RuntimeException()).isLoading())
    }

    @Test
    fun `isSuccess should return true only for Success state`() {
        assertFalse(AsyncState.Idle<Unit>().isSuccess())
        assertFalse(AsyncState.Loading<Unit>().isSuccess())
        assertTrue(AsyncState.Success(Unit).isSuccess())
        assertFalse(AsyncState.Error<Unit>(RuntimeException()).isSuccess())
    }

    @Test
    fun `isError should return true only for Error state`() {
        assertFalse(AsyncState.Idle<Unit>().isError())
        assertFalse(AsyncState.Loading<Unit>().isError())
        assertFalse(AsyncState.Success(Unit).isError())
        assertTrue(AsyncState.Error<Unit>(RuntimeException()).isError())
    }

    @Test
    fun `isTerminal should return true for Success and Error`() {
        assertFalse(AsyncState.Idle<Unit>().isTerminal())
        assertFalse(AsyncState.Loading<Unit>().isTerminal())
        assertTrue(AsyncState.Success(Unit).isTerminal())
        assertTrue(AsyncState.Error<Unit>(RuntimeException()).isTerminal())
    }
    // endregion

    // region Callbacks
    @Test
    fun `onSuccess should be called only for Success state`() {
        var called = false
        AsyncState.Success("data").onSuccess {
            called = true
            assertEquals("data", it)
        }
        assertTrue(called)

        called = false
        AsyncState.Idle<String>().onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun `onError should be called only for Error state`() {
        var called = false
        val error = RuntimeException()
        AsyncState.Error<String>(error).onError {
            called = true
            assertEquals(error, it)
        }
        assertTrue(called)

        called = false
        AsyncState.Success("data").onError { called = true }
        assertFalse(called)
    }

    @Test
    fun `onLoading should be called only for Loading state`() {
        var called = false
        AsyncState.Loading<String>(0.5f).onLoading {
            called = true
            assertEquals(0.5f, it)
        }
        assertTrue(called)

        called = false
        AsyncState.Success("data").onLoading { called = true }
        assertFalse(called)
    }

    @Test
    fun `onIdle should be called only for Idle state`() {
        var called = false
        AsyncState.Idle<String>().onIdle {
            called = true
        }
        assertTrue(called)

        called = false
        AsyncState.Success("data").onIdle { called = true }
        assertFalse(called)
    }
    // endregion

    // region Map functions
    @Test
    fun `map function should transform Success data and preserve other states`() {
        val successState = AsyncState.Success(10)
        val mappedSuccess = successState.map { it * 2 }
        assertEquals(AsyncState.Success(20), mappedSuccess)

        val error = RuntimeException()
        val errorState = AsyncState.Error<Int>(error)
        val mappedError = errorState.map { it * 2 }
        assertEquals(AsyncState.Error(error), mappedError)

        val loadingState = AsyncState.Loading<Int>()
        val mappedLoading = loadingState.map { it * 2 }
        assertEquals(AsyncState.Loading(), mappedLoading)

        val idleState = AsyncState.Idle<Int>()
        val mappedIdle = idleState.map { it * 2 }
        assertEquals(AsyncState.Idle(), mappedIdle)
    }

    @Test
    fun `mapNull should map null data in Success to provided block`() {
        val result = AsyncState.Success<String?>(null).mapNull {
            AsyncState.Success("Default Value")
        }
        assertIs<AsyncState.Success<String>>(result)
        assertEquals("Default Value", result.data)
    }

    @Test
    fun `mapNull should map existing data in all states to the same thing`() {
        val default = AsyncState.Success("Default Value")

        val success = AsyncState.Success<String?>("Existing data").mapNull { default }
        assertIs<AsyncState.Success<String>>(success)
        assertEquals("Existing data", success.data)

        val idle = AsyncState.Idle<String?>().mapNull { default }
        assertIs<AsyncState.Idle<String>>(idle)

        val loading = AsyncState.Loading<String?>(0.9f).mapNull { default }
        assertIs<AsyncState.Loading<String>>(loading)
        assertEquals(0.9f, loading.progress)

        val error = AsyncState.Error<String?>(IllegalStateException("Uh oh")).mapNull { default }
        assertIs<AsyncState.Error<String>>(error)
        assertEquals("Uh oh", error.error.message)
    }

    @Test
    fun `mapNullAsError should map null data in Success to Error`() {
        val nullSuccess: AsyncState<String?> = AsyncState.Success(null)
        val result = nullSuccess.mapNullAsError()
        assertIs<AsyncState.Error<String?>>(result)
        assertIs<NullPointerException>((result as AsyncState.Error).error)
    }

    @Test
    fun `mapNullAsIdle should map null data in Success to Idle`() {
        val nullSuccess: AsyncState<String?> = AsyncState.Success(null)
        val result = nullSuccess.mapNullAsIdle()
        assertIs<AsyncState.Idle<String?>>(result)
    }
    // endregion

    @Test
    fun `toUnit should remove Success data`() {
        val result = AsyncState.Success("data").toUnit()
        assertIs<AsyncState.Success<Unit>>(result)
        assertEquals(Unit, result.data)
    }

    @Test
    fun `mapNullToError should convert Success with null to Error and pass other states`() =
        runTest {
            val error = RuntimeException("Test Error")
            val sourceFlow = flowOf<AsyncState<String?>>(
                AsyncState.Idle(),
                AsyncState.Loading(),
                AsyncState.Success("Data"),
                AsyncState.Success(null),
                AsyncState.Error(error)
            )

            val result = sourceFlow.mapNullToError().toList()

            assertIs<AsyncState.Idle<String>>(result[0])
            assertIs<AsyncState.Loading<String>>(result[1])

            val successState = result[2]
            assertIs<AsyncState.Success<String>>(successState)
            assertEquals("Data", successState.data)

            val nullAsErrorState = result[3]
            assertIs<AsyncState.Error<String>>(nullAsErrorState)
            assertIs<NullPointerException>(nullAsErrorState.error)

            val errorState = result[4]
            assertIs<AsyncState.Error<String>>(errorState)
            assertEquals(error, errorState.error)
        }

    @Test
    fun `mapLatestSuccess should apply transformation to Success state only`() = runTest {
        val error = RuntimeException("Test Error")
        val sourceFlow = flowOf(
            AsyncState.Idle(),
            AsyncState.Loading(),
            AsyncState.Success(10),
            AsyncState.Error(error)
        )

        val result = sourceFlow.mapLatestSuccess { data -> "Value: $data" }.toList()

        assertIs<AsyncState.Idle<String>>(result[0])
        assertIs<AsyncState.Loading<String>>(result[1])

        val successState = result[2]
        assertIs<AsyncState.Success<String>>(successState)
        assertEquals("Value: 10", successState.data)

        val errorState = result[3]
        assertIs<AsyncState.Error<String>>(errorState)
        assertEquals(error, errorState.error)
    }

    @Test
    fun `onEachIdle should trigger action only for Idle state`() = runTest {
        var idleCalled = false
        val sourceFlow = flowOf(
            AsyncState.Idle(),
            AsyncState.Loading<Unit>()
        )

        sourceFlow.onEachIdle { idleCalled = true }.toList() // .toList() consumes the flow

        assertTrue(idleCalled, "onEachIdle should be called for Idle state")
    }

    @Test
    fun `onEachLoading should trigger action only for Loading state`() = runTest {
        var loadingProgress: Float? = -1f // Using a sentinel value
        val sourceFlow = flowOf(
            AsyncState.Success("data"),
            AsyncState.Loading(0.75f)
        )

        sourceFlow.onEachLoading { progress -> loadingProgress = progress }.toList()

        assertEquals(
            0.75f,
            loadingProgress,
            "onEachLoading should be called with the correct progress"
        )
    }

    @Test
    fun `onEachSuccess should trigger action only for Success state`() = runTest {
        var receivedData: String? = null
        val sourceFlow = flowOf(
            AsyncState.Error(RuntimeException()),
            AsyncState.Success("Test Data")
        )

        sourceFlow.onEachSuccess { data -> receivedData = data }.toList()

        assertEquals(
            "Test Data",
            receivedData,
            "onEachSuccess should be called with the correct data"
        )
    }

    @Test
    fun `onEachError should trigger action only for Error state`() = runTest {
        var receivedError: Throwable? = null
        val testError = RuntimeException("Test")
        val sourceFlow = flowOf(
            AsyncState.Success("data"),
            AsyncState.Error(testError)
        )

        sourceFlow.onEachError { error -> receivedError = error }.toList()

        assertEquals(
            testError,
            receivedError,
            "onEachError should be called with the correct error"
        )
    }

    @Test
    fun `asUnit should convert data type to Unit for all states`() {
        val error = RuntimeException()

        // Test case: Success
        val successState = AsyncState.Success("data")
        val unitSuccess = successState.asUnit()
        assertIs<AsyncState.Success<Unit>>(unitSuccess)
        assertEquals(Unit, unitSuccess.data)

        // Test case: Error
        val errorState = AsyncState.Error<String>(error)
        val unitError = errorState.asUnit()
        assertIs<AsyncState.Error<Unit>>(unitError)
        assertEquals(error, unitError.error)

        // Test case: Loading
        val loadingState = AsyncState.Loading<String>(0.5f)
        val unitLoading = loadingState.asUnit()
        assertIs<AsyncState.Loading<Unit>>(unitLoading)
        assertEquals(0.5f, unitLoading.progress)

        // Test case: Idle
        val idleState = AsyncState.Idle<String>()
        val unitIdle = idleState.asUnit()
        assertIs<AsyncState.Idle<Unit>>(unitIdle)
    }
}
