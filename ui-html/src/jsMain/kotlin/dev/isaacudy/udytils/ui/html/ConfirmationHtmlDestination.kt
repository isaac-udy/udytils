package dev.isaacudy.udytils.ui.html

import androidx.compose.runtime.Composable
import dev.enro.annotations.NavigationDestination
import dev.enro.complete
import dev.enro.html.htmlDialog
import dev.enro.requestClose
import dev.enro.ui.NavigationDestinationProvider
import dev.enro.ui.navigationDestinationContent
import dev.isaacudy.udytils.ui.destinations.ConfirmationDestination
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * The Compose HTML destination for [ConfirmationDestination.Default]: a modal `<dialog>` with the
 * title, the message and two buttons. Confirming completes the key; dismissing, Escape or a
 * backdrop click closes it without completing.
 */
@NavigationDestination.PlatformOverride(ConfirmationDestination.Default::class)
val confirmationHtmlDestination: NavigationDestinationProvider<ConfirmationDestination.Default> =
    navigationDestinationContent(metadata = { htmlDialog() }) {
        val key = navigation.key
        ConfirmationHtmlContent(
            title = key.title.asHtmlString(),
            message = key.message?.asHtmlString(),
            confirmText = key.confirmText.asHtmlString(),
            dismissText = key.dismissText.asHtmlString(),
            destructive = key.destructive,
            onConfirm = { navigation.complete() },
            onDismiss = { navigation.requestClose() },
        )
    }

@Composable
internal fun ConfirmationHtmlContent(
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val htmlClasses = LocalUdytilsHtmlClasses.current
    Div(attrs = { classes(htmlClasses.dialogContent) }) {
        H2(attrs = { classes(htmlClasses.title) }) { Text(title) }
        if (message != null) {
            P(attrs = { classes(htmlClasses.body) }) { Text(message) }
        }
        Div(attrs = { classes(htmlClasses.actions) }) {
            Button(attrs = {
                classes(htmlClasses.dismissButton)
                onClick { onDismiss() }
            }) { Text(dismissText) }
            Button(attrs = {
                classes(if (destructive) htmlClasses.destructiveButton else htmlClasses.confirmButton)
                onClick { onConfirm() }
            }) { Text(confirmText) }
        }
    }
}
