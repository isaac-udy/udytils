package dev.isaacudy.udytils.samples

import androidx.compose.runtime.Composable
import dev.enro.asInstance
import dev.enro.ui.NavigationDisplay
import dev.enro.ui.rememberNavigationContainer
import dev.isaacudy.udytils.samples.home.HomeDestination

@Composable
fun SamplesApplicationContent() {
    val rootNavigationContainer = rememberNavigationContainer(
        backstack = listOf(
            HomeDestination.asInstance()
        )
    )
    SamplesTheme {
        NavigationDisplay(rootNavigationContainer)
    }
}
