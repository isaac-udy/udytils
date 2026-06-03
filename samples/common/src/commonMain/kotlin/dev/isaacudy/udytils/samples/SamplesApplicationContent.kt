package dev.isaacudy.udytils.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.enro.asInstance
import dev.enro.ui.NavigationDisplay
import dev.enro.ui.rememberNavigationContainer
import dev.isaacudy.udytils.samples.home.HomeDestination
import dev.isaacudy.udytils.samples.theme.SamplesTheme

@Composable
fun SamplesApplicationContent() {
    val rootNavigationContainer = rememberNavigationContainer(
        backstack = listOf(
            HomeDestination.asInstance()
        )
    )
    SamplesTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .height(paddingValues.calculateTopPadding())
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(paddingValues)
                    .padding(paddingValues)
            ) {
                NavigationDisplay(
                    state = rootNavigationContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
