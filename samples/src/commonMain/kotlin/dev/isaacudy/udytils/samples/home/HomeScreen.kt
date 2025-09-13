package dev.isaacudy.udytils.samples.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.enro.NavigationKey
import dev.enro.annotations.NavigationDestination
import dev.enro.navigationHandle
import dev.enro.open
import dev.isaacudy.udytils.samples.confirmation.ConfirmationSamplesDestination
import dev.isaacudy.udytils.ui.components.ListCard
import kotlinx.serialization.Serializable

@Serializable
object HomeDestination : NavigationKey

@Composable
@NavigationDestination(HomeDestination::class)
fun HomeScreen() {
    val navigation = navigationHandle<HomeDestination>()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Home") },
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
            ListCard(
                title = {
                    Text("Confirmation Destination")
                },
                onClick = {
                    navigation.open(ConfirmationSamplesDestination)
                }
            )
            Spacer(Modifier.padding(top = 32.dp))
        }
    }
}
