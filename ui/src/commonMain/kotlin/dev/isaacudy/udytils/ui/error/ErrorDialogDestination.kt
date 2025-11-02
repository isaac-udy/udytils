package dev.isaacudy.udytils.ui.error

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.enro.NavigationKey
import dev.enro.annotations.NavigationDestination
import dev.enro.complete
import dev.enro.requestClose
import dev.isaacudy.udytils.error.ErrorMessage
import dev.isaacudy.udytils.ui.destinations.floatingCardDestination
import kotlinx.serialization.Serializable

@Serializable
data class ErrorDialogDestination(
    val errorMessage: ErrorMessage,
    val retryEnabled: Boolean,
) : NavigationKey

@NavigationDestination(ErrorDialogDestination::class)
val errorDialogDestination = floatingCardDestination<ErrorDialogDestination>(
    minWidth = 300.dp
) {
    ErrorDialogContent(
        id = navigation.key.errorMessage.id,
        title = navigation.key.errorMessage.title,
        message = navigation.key.errorMessage.message,
        isRetryable = navigation.key.errorMessage.retryable && navigation.key.retryEnabled,
        onRetryClick = { navigation.complete() },
        onCloseClick = { navigation.requestClose() }
    )
}

@Composable
private fun ErrorDialogContent(
    id: String,
    title: String,
    message: String,
    isRetryable: Boolean,
    onRetryClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .widthIn(min = 300.dp, max = 400.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = id,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isRetryable) {
            Button(
                onClick = onRetryClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Retry")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onCloseClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close")
        }
    }
}
