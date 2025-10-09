package dev.isaacudy.udytils.ui.destinations

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.enro.NavigationKey
import dev.enro.requestClose
import dev.enro.ui.NavigationDestinationProvider
import dev.enro.ui.NavigationDestinationScope
import dev.enro.ui.navigationDestination
import dev.enro.ui.scenes.directOverlay


/**
 * Creates a navigation destination that displays content within a floating card dialog.
 *
 * This function provides a reusable navigation destination pattern where the content
 * is displayed in a Material3 Card component within a Dialog. The dialog is configured
 * with directOverlay metadata, which makes it appear as an overlay directly on top of
 * the current screen without pushing it aside. The card has a minimum width constraint
 * of 500dp and includes padding around its edges. When the dialog is dismissed, the
 * associated navigation handle will be closed.
 *
 * @param T The type of NavigationKey that this destination handles
 * @param content A composable function that defines the content to be displayed within
 * the floating card. The content has access to the NavigationDestinationScope for
 * navigation operations.
 * @return A NavigationDestinationProvider that can be used with Enro's navigation system
 */
fun <T : NavigationKey> floatingCardDestination(
    minWidth: Dp = 500.dp,
    content: @Composable NavigationDestinationScope<T>.() -> Unit
): NavigationDestinationProvider<T> {
    return navigationDestination(
        metadata = {
            directOverlay()
        }
    ) {
        Dialog(
            onDismissRequest = {
                navigation.requestClose()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(min = minWidth)
            ) {
                content()
            }
        }
    }
}