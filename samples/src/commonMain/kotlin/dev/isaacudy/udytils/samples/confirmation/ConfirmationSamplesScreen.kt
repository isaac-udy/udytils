package dev.isaacudy.udytils.samples.confirmation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.enro.NavigationKey
import dev.enro.annotations.NavigationDestination
import dev.enro.result.open
import dev.enro.result.registerForNavigationResult
import dev.isaacudy.udytils.samples.scaffold.SampleScreen
import dev.isaacudy.udytils.ui.components.ContentCard
import dev.isaacudy.udytils.ui.components.LabelText
import dev.isaacudy.udytils.ui.destinations.ConfirmationDestination
import kotlinx.serialization.Serializable

@Serializable
object ConfirmationSamplesDestination : NavigationKey

@Composable
@NavigationDestination(ConfirmationSamplesDestination::class)
fun ConfirmationSamplesScreen() {
    val confirmationResult = remember { mutableStateOf<String?>(null) }
    val confirmationChannel = registerForNavigationResult(
        onClosed = { confirmationResult.value = "Dismissed" },
        onCompleted = { confirmationResult.value = "Confirmed" },
    )

    SampleScreen(
        title = "ConfirmationDestination",
        documentation = confirmationDocs,
        sourceFiles = listOf(
            "ui/src/commonMain/.../ui/destinations/ConfirmationDestination.kt",
            "samples/src/commonMain/.../samples/confirmation/ConfirmationSamplesScreen.kt",
        ),
    ) {
        ContentCard(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabelText.Large(text = "Last result: ${confirmationResult.value ?: "(none)"}")
            OutlinedButton(
                onClick = {
                    confirmationChannel.open(
                        ConfirmationDestination(title = "Confirm Basic"),
                    )
                }
            ) { Text("Confirm Basic") }

            OutlinedButton(
                onClick = {
                    confirmationChannel.open(
                        ConfirmationDestination(
                            title = "Confirm Message",
                            message = "Are you really sure that you want to confirm the action?",
                            confirmText = "Yes, I'm sure",
                            dismissText = "No, cancel",
                        ),
                    )
                }
            ) { Text("Confirm With Message") }

            OutlinedButton(
                onClick = {
                    confirmationChannel.open(
                        ConfirmationDestination(
                            title = "Confirm Destructive",
                            confirmText = "Delete",
                            destructive = true,
                        ),
                    )
                }
            ) { Text("Confirm Destructive") }

            OutlinedButton(
                enabled = confirmationResult.value != null,
                onClick = { confirmationResult.value = null },
            ) { Text("Clear Result") }
        }
    }
}

private const val confirmationDocs = """
`ConfirmationDestination` is a navigation destination that asks the user to confirm an action.

There is currently one display style, `ConfirmationDestination.Default`, rendered as an `AlertDialog`. Additional styles may be added in the future.

## Parameters
- `title` — title for the confirmation.
- `message` — optional message body.
- `confirmText` — confirm button label (defaults to "Confirm").
- `dismissText` — dismiss button label (defaults to "Close").
- `destructive` — when true, renders the confirm button in the error color.
"""
