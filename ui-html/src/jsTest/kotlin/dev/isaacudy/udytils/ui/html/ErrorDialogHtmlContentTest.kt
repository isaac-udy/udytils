package dev.isaacudy.udytils.ui.html

import org.jetbrains.compose.web.testutils.ComposeWebExperimentalTestsApi
import org.jetbrains.compose.web.testutils.runTest
import org.w3c.dom.asList
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ComposeWebExperimentalTestsApi::class)
class ErrorDialogHtmlContentTest {
    @Test
    fun showsTheTitleIdAndMessage() = runTest {
        composition {
            ErrorDialogHtmlContent(
                id = "RXJyb3I=",
                title = "Couldn't save",
                message = "Check your connection and try again.",
                isRetryable = false,
                onRetry = {},
                onClose = {},
            )
        }

        assertEquals("Couldn't save", root.querySelector("h2")?.textContent)
        assertEquals(
            listOf("RXJyb3I=", "Check your connection and try again."),
            root.querySelectorAll("p").asList().map { it.textContent },
        )
    }

    @Test
    fun aRetryableErrorOffersRetry() = runTest {
        var retries = 0
        var closes = 0
        composition {
            ErrorDialogHtmlContent(
                id = "RXJyb3I=",
                title = "Couldn't save",
                message = "Check your connection and try again.",
                isRetryable = true,
                onRetry = { retries++ },
                onClose = { closes++ },
            )
        }

        root.button("Retry").click()
        root.button("Close").click()

        assertEquals(1, retries)
        assertEquals(1, closes)
    }

    @Test
    fun anErrorThatIsNotRetryableOffersOnlyClose() = runTest {
        composition {
            ErrorDialogHtmlContent(
                id = "RXJyb3I=",
                title = "Couldn't save",
                message = "Check your connection and try again.",
                isRetryable = false,
                onRetry = {},
                onClose = {},
            )
        }

        assertEquals(listOf("Close"), root.buttons().map { it.textContent })
    }
}
