package dev.isaacudy.udytils.samples.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.isaacudy.udytils.coroutines.JobManager
import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.fromSuspending
import dev.isaacudy.udytils.state.viewModelState
import kotlinx.coroutines.delay

class AsyncStateSamplesViewModel : ViewModel() {

    val state = viewModelState(
        initialState = AsyncStateSamplesState(
            asyncState = AsyncState.Idle()
        )
    )
    private val jobManager = JobManager(viewModelScope)

    fun runSuccess() {
        jobManager.launchReplacing(Unit) {
            AsyncState
                .fromSuspending {
                    emitProgress(0f)
                    delay(1000)
                    emitProgress(0.33f)
                    delay(1000)
                    emitProgress(0.66f)
                    delay(1000)
                    emitProgress(1f)
                    return@fromSuspending 42
                }
                .collect {
                    state.update {
                        copy(asyncState = it)
                    }
                }
        }
    }

    fun runFailure() {
        jobManager.launchReplacing(Unit) {
            AsyncState
                .fromSuspending<Int> {
                    emitIndeterminateProgress()
                    delay(2000)
                    error("Simulated failure")
                }
                .collect {
                    state.update {
                        copy(asyncState = it)
                    }
                }
        }
    }

    fun reset() {
        jobManager.launchReplacing(Unit) {
            state.update {
                copy(
                    asyncState = AsyncState.Idle(),
                )
            }
        }
    }
}
