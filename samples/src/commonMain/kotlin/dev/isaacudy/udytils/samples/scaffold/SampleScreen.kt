package dev.isaacudy.udytils.samples.scaffold

import androidx.compose.foundation.layout.Arrangement
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
import com.mikepenz.markdown.m3.Markdown
import dev.isaacudy.udytils.samples.theme.SamplesTheme
import dev.isaacudy.udytils.samples.theme.markdownTypography
import dev.isaacudy.udytils.ui.components.BodyText
import dev.isaacudy.udytils.ui.components.ContentCard
import dev.isaacudy.udytils.ui.components.LabelText

@Composable
fun SampleScreen(
    title: String,
    documentation: String,
    sourceFiles: List<String> = emptyList(),
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.padding(top = 8.dp))
            ContentCard(modifier = Modifier.fillMaxWidth()) {
                Markdown(
                    content = documentation,
                    typography = SamplesTheme.markdownTypography,
                )
            }
            if (sourceFiles.isNotEmpty()) {
                ContentCard(modifier = Modifier.fillMaxWidth()) {
                    LabelText.Large(text = "Source")
                    Spacer(Modifier.padding(top = 4.dp))
                    sourceFiles.forEach { path ->
                        BodyText.Small(
                            text = path,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
            Spacer(Modifier.padding(top = 32.dp))
        }
    }
}
