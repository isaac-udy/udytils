package dev.isaacudy.udytils.samples.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.state.fromSuspending
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AsyncStateSamplesViewModel : ViewModel() {

    private val _state = MutableStateFlow<AsyncState<Int>>(AsyncState.Idle())
    val state: StateFlow<AsyncState<Int>> = _state.asStateFlow()

    private var currentRun: Job? = null

    fun runSuccess() {
        currentRun?.cancel()
        currentRun = viewModelScope.launch {
            AsyncState.fromSuspending<Int> {
                emitProgress(0f)
                delay(400)
                emitProgress(0.33f)
                delay(400)
                emitProgress(0.66f)
                delay(400)
                emitProgress(1f)
                42
            }.collect { _state.value = it }
        }
    }

    fun runFailure() {
        currentRun?.cancel()
        currentRun = viewModelScope.launch {
            AsyncState.fromSuspending<Int> {
                emitIndeterminateProgress()
                delay(800)
                error("Simulated failure")
            }.collect { _state.value = it }
        }
    }

    fun reset() {
        currentRun?.cancel()
        _state.value = AsyncState.Idle()
    }
}
