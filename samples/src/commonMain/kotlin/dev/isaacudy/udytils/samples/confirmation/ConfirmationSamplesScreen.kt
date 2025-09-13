package dev.isaacudy.udytils.samples.confirmation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import dev.enro.NavigationKey
import dev.enro.annotations.NavigationDestination
import dev.enro.navigationHandle
import dev.enro.result.open
import dev.enro.result.registerForNavigationResult
import dev.isaacudy.udytils.samples.theme.SamplesTheme
import dev.isaacudy.udytils.samples.theme.markdownTypography
import dev.isaacudy.udytils.ui.components.ContentCard
import dev.isaacudy.udytils.ui.destinations.ConfirmationDestination
import kotlinx.serialization.Serializable


@Serializable
object ConfirmationSamplesDestination : NavigationKey

@Composable
@NavigationDestination(ConfirmationSamplesDestination::class)
fun ConfirmationSamplesScreen() {
    val navigation = navigationHandle<ConfirmationSamplesDestination>()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val confirmationResult = remember { mutableStateOf<String?>(null) }
    val confirmationChannel = registerForNavigationResult(
        onClosed = {
            confirmationResult.value = "Dismissed"
        },
        onCompleted = {
            confirmationResult.value = "Confirmed"
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmation Destination") },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.padding(top = 8.dp))
            ContentCard(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Markdown(
                    content = confirmationSamplesReadMe,
                    typography = SamplesTheme.markdownTypography,
                )
            }

            ContentCard(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Markdown(
                    content = """
                        # Samples
                        ###### 
                        ###### Confirmation Result: 
                        ${
                        when {
                            confirmationResult.value == null -> "(none)"
                            else -> "# ${confirmationResult.value}"
                        }
                    }
                        ###### 
                    """.trimIndent(),
                    typography = SamplesTheme.markdownTypography,
                )

                OutlinedButton(
                    onClick = {
                        confirmationChannel.open(
                            ConfirmationDestination(
                                title = "Confirm Basic",
                            )
                        )
                    }
                ) {
                    Text("Confirm Basic")
                }
                OutlinedButton(
                    onClick = {
                        confirmationChannel.open(
                            ConfirmationDestination(
                                title = "Confirm Message",
                                message = "Are you really sure that you want to confirm the action?",
                                confirmText = "Yes, I'm sure",
                                dismissText = "No, cancel",
                            )
                        )
                    }
                ) {
                    Text("Confirm Message")
                }
                OutlinedButton(
                    onClick = {
                        confirmationChannel.open(
                            ConfirmationDestination(
                                title = "Confirm Destructive",
                                confirmText = "Delete",
                                destructive = true,
                            )
                        )
                    }
                ) {
                    Text("Confirm Destructive")
                }
                OutlinedButton(
                    enabled = confirmationResult.value != null,
                    onClick = {
                        confirmationResult.value = null
                    }
                ) {
                    Text("Clear Result")
                }

            }

            Spacer(Modifier.padding(top = 32.dp))
        }
    }
}

private const val confirmationSamplesReadMe = """
# Readme
`ConfirmationDestination` is a navigation destination that can be used to show a screen that requires the user to confirm an action.

There is currently one display style for `ConfirmationDestination` , which is `ConfirmationDestination.Default`, which displays the confirmation content using an AlertDialog. In the future, there may be additional styles that display the confirmation screen in different ways.

## Parameters
`ConfirmationDestination` takes the following parameters:
- `title`: The title for the confirmation.
- `message`: An optional message to display with the confirmation.
- `confirmText`: The text of the confirm button (defaults to "Confirm").
- `dismissText`: The text of the dismiss button (defaults to "Close").
- `destructive`: Whether the confirmation is destructive (defaults to false). This will change the color of the confirm button to red.
"""