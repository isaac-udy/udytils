package dev.isaacudy.udytils.ui.html

import androidx.compose.runtime.Composable
import dev.enro.annotations.NavigationDestination
import dev.enro.complete
import dev.enro.html.htmlDialog
import dev.enro.requestClose
import dev.enro.ui.NavigationDestinationProvider
import dev.enro.ui.navigationDestinationContent
import dev.isaacudy.udytils.ui.error.ErrorDialogDestination
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * The Compose HTML destination for [ErrorDialogDestination], which `ErrorHandler` opens by default:
 * a modal `<dialog>` with the error's title, id and message. Retry, shown when the error is
 * retryable and the handler has a retry action, completes the key; Close closes it.
 */
@NavigationDestination.PlatformOverride(ErrorDialogDestination::class)
val errorDialogHtmlDestination: NavigationDestinationProvider<ErrorDialogDestination> =
    navigationDestinationContent(metadata = { htmlDialog() }) {
        val errorMessage = navigation.key.errorMessage
        ErrorDialogHtmlContent(
            id = errorMessage.id,
            title = errorMessage.title.asHtmlString(),
            message = errorMessage.message.asHtmlString(),
            isRetryable = errorMessage.retryable && navigation.key.retryEnabled,
            onRetry = { navigation.complete() },
            onClose = { navigation.requestClose() },
        )
    }

@Composable
internal fun ErrorDialogHtmlContent(
    id: String,
    title: String,
    message: String,
    isRetryable: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val htmlClasses = LocalUdytilsHtmlClasses.current
    Div(attrs = { classes(htmlClasses.dialogContent) }) {
        H2(attrs = { classes(htmlClasses.title) }) { Text(title) }
        P(attrs = { classes(htmlClasses.caption) }) { Text(id) }
        P(attrs = { classes(htmlClasses.body) }) { Text(message) }
        Div(attrs = { classes(htmlClasses.actions) }) {
            Button(attrs = {
                classes(htmlClasses.dismissButton)
                onClick { onClose() }
            }) { Text("Close") }
            if (isRetryable) {
                Button(attrs = {
                    classes(htmlClasses.confirmButton)
                    onClick { onRetry() }
                }) { Text("Retry") }
            }
        }
    }
}
