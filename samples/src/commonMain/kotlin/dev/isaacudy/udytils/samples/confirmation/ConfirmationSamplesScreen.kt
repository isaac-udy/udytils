package dev.isaacudy.udytils.samples.confirmation

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import dev.enro.NavigationKey
import dev.enro.annotations.NavigationDestination
import kotlinx.serialization.Serializable


@Serializable
object ConfirmationSamplesDestination : NavigationKey

@Composable
@NavigationDestination(ConfirmationSamplesDestination::class)
fun ConfirmationSamplesScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmation") }
            )
        }
    ) {

    }
}