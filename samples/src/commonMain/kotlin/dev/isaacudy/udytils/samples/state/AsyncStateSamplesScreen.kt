package dev.isaacudy.udytils.samples.state

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import dev.enro.NavigationKey
import dev.enro.annotations.NavigationDestination
import dev.enro.navigationHandle
import dev.isaacudy.udytils.samples.theme.SamplesTheme
import dev.isaacudy.udytils.samples.theme.markdownTypography
import dev.isaacudy.udytils.ui.components.ContentCard
import kotlinx.serialization.Serializable

@Serializable
object AsyncStateSamplesDestination : NavigationKey

@Composable
@NavigationDestination(AsyncStateSamplesDestination::class)
fun AsyncStateSamplesScreen() {
    val navigation = navigationHandle<AsyncStateSamplesDestination>()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val viewModel = AsyncStateSamplesViewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AsyncState") },
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

            // Documentation
            ContentCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Markdown(
                    content = asyncStateSamplesReadMe,
                    typography = SamplesTheme.markdownTypography,
                )
            }
        }
    }
}


private const val asyncStateSamplesReadMe = """
# Readme
`AsyncState` is a sealed class that represents the state of an asynchronous operation. It has four possible states:

- **Idle**: No operation has been started
- **Loading**: Operation is in progress
- **Success**: Operation completed successfully with data
- **Error**: Operation failed with an error

"""