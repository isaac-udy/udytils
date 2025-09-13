package dev.isaacudy.udytils.samples.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.enro.NavigationKey
import dev.enro.annotations.NavigationDestination
import kotlinx.serialization.Serializable

@Serializable
object HomeDestination : NavigationKey

@Composable
@NavigationDestination(HomeDestination::class)
fun HomeScreen() {
    Text("Samples Home")
}
