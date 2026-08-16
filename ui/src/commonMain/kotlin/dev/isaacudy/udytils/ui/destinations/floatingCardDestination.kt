package dev.isaacudy.udytils.ui.destinations

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
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
 * of 500dp and a maximum width constraint of 560dp (Material's dialog max width) by
 * default, and includes padding around its edges. When the dialog is dismissed, the
 * associated navigation handle will be closed.
 *
 * The card is also kept clear of the edges of the window: it takes at most
 * [MAX_HEIGHT_FRACTION] of the height available to it, so there is always a band of scrim above and
 * below it — on a phone, tapping outside the card is the way out of a dialog, and content long
 * enough to fill the window left nothing to tap. Content that can outgrow that bound is responsible
 * for scrolling within it, as it already had to be for content taller than the window.
 *
 * @param T The type of NavigationKey that this destination handles
 * @param minWidth The minimum width the card is allowed to shrink to.
 * @param maxWidth The maximum width the card is allowed to grow to. If a caller passes a
 * [minWidth] larger than this, the effective max is coerced up to [minWidth] so the
 * constraint stays well-formed.
 * @param content A composable function that defines the content to be displayed within
 * the floating card. The content has access to the NavigationDestinationScope for
 * navigation operations.
 * @return A NavigationDestinationProvider that can be used with Enro's navigation system
 */
fun <T : NavigationKey> floatingCardDestination(
    minWidth: Dp = 500.dp,
    maxWidth: Dp = 560.dp,
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
            BoxWithConstraints {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .padding(16.dp)
                        .widthIn(min = minWidth, max = maxOf(minWidth, maxWidth))
                        .heightIn(
                            // Unspecified is `heightIn`'s own "no bound", which is the right answer
                            // when there is no finite height to take a fraction of.
                            max = when {
                                maxHeight.value.isFinite() -> maxHeight * MAX_HEIGHT_FRACTION
                                else -> Dp.Unspecified
                            }
                        )
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * How much of the window a floating card may occupy. The remainder is scrim, and the scrim is a
 * dismiss gesture — the 16dp of padding alone was not something a thumb could reliably find.
 */
private const val MAX_HEIGHT_FRACTION = 0.85f