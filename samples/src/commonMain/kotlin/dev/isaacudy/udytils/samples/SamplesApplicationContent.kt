package dev.isaacudy.udytils.samples

import androidx.compose.runtime.Composable
import dev.enro.ui.NavigationDisplay
import dev.enro.ui.rememberNavigationContainer

@Composable
fun SamplesApplicationContent() {
    val rootNavigationContainer = rememberNavigationContainer(
        backstack = emptyList()
    )
    SamplesTheme {
        NavigationDisplay(rootNavigationContainer)
    }
}
