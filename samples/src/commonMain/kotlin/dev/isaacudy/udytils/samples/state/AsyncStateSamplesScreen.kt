package dev.isaacudy.udytils.samples.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.enro.NavigationKey
import dev.enro.annotations.NavigationDestination
import dev.enro.navigationHandle
import dev.enro.requestClose
import dev.isaacudy.udytils.samples.scaffold.SampleScreen
import dev.isaacudy.udytils.state.AsyncState
import dev.isaacudy.udytils.ui.components.BodyText
import dev.isaacudy.udytils.ui.components.ContentCard
import dev.isaacudy.udytils.ui.components.LabelText
import kotlinx.serialization.Serializable

@Serializable
object AsyncStateSamplesDestination : NavigationKey

@Composable
@NavigationDestination(AsyncStateSamplesDestination::class)
fun AsyncStateSamplesScreen() {
    val navigation = navigationHandle<AsyncStateSamplesDestination>()
    val viewModel = viewModel { AsyncStateSamplesViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    SampleScreen(
        title = "AsyncState",
        documentation = asyncStateDocs,
        sourceFiles = listOf(
            "core/src/commonMain/.../state/AsyncState.kt",
            "core/src/commonMain/.../state/AsyncState.fromSuspending.kt",
            "samples/src/commonMain/.../samples/state/AsyncStateSamplesScreen.kt",
            "samples/src/commonMain/.../samples/state/AsyncStateSamplesViewModel.kt",
        ),
        onBackClick = { navigation.requestClose() },
    ) {
        ContentCard(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabelText.Large(text = "Current state")
            AsyncStateDisplay(state = state)
        }

        ContentCard(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabelText.Large(text = "Trigger")
            OutlinedButton(onClick = viewModel::runSuccess) {
                Text("Run with progress (success)")
            }
            OutlinedButton(onClick = viewModel::runFailure) {
                Text("Run with indeterminate progress (failure)")
            }
            OutlinedButton(onClick = viewModel::reset) {
                Text("Reset to Idle")
            }
        }
    }
}

@Composable
private fun AsyncStateDisplay(state: AsyncState<Int>) {
    when (state) {
        is AsyncState.Idle -> {
            BodyText(text = "Idle — press a trigger to start.")
        }

        is AsyncState.Loading -> {
            val progress = state.progress
            if (progress != null) {
                BodyText(text = "Loading… ${(progress * 100).toInt()}%")
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                BodyText(text = "Loading (indeterminate)…")
                CircularProgressIndicator()
            }
        }

        is AsyncState.Success -> {
            BodyText(
                text = "Success: ${state.data}",
                color = MaterialTheme.colorScheme.primary,
            )
        }

        is AsyncState.Error -> {
            BodyText(
                text = "Error: ${state.error.message ?: state.error::class.simpleName}",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private const val asyncStateDocs = """
`AsyncState<T>` is a sealed class with four states:

- `Idle` — no operation has started
- `Loading(progress: Float?)` — operation in progress, optionally with a 0..1 progress value
- `Success(data: T)` — operation completed
- `Error(error: Throwable)` — operation failed

## Construction
The idiomatic way to build a flow of `AsyncState` is via `fromSuspending`:

The block receives a `FromSuspendingScope` you can use to call `emitProgress(...)` or `emitIndeterminateProgress()` while the work runs. The returned `Flow<AsyncState<T>>` emits `Loading` (with progress updates), then `Success` on completion, or `Error` if the block throws.

## Try it
Below, a ViewModel collects from `AsyncState.fromSuspending` into a `MutableStateFlow<AsyncState<Int>>` that this screen renders.
"""
