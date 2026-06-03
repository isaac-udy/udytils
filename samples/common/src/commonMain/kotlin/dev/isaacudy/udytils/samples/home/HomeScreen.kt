package dev.isaacudy.udytils.samples.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import dev.isaacudy.udytils.samples.catalog.SamplesCatalog
import dev.isaacudy.udytils.ui.components.BodyText
import dev.isaacudy.udytils.ui.components.HeadlineText
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
                title = { Text("Udytils samples") },
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
            SamplesCatalog.categories.forEach { category ->
                Spacer(Modifier.padding(top = 16.dp))
                HeadlineText.Small(text = category.title)
                if (category.description != null) {
                    Spacer(Modifier.padding(top = 2.dp))
                    BodyText.Small(
                        text = category.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.padding(top = 8.dp))
                category.samples.forEach { sample ->
                    ListCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = { Text(sample.title) },
                        subtitle = { Text(sample.description) },
                        onClick = { navigation.open(sample.key) },
                    )
                }
            }
            Spacer(Modifier.padding(top = 32.dp))
        }
    }
}
