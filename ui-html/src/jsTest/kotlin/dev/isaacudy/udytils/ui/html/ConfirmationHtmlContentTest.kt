package dev.isaacudy.udytils.ui.html

import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.web.testutils.ComposeWebExperimentalTestsApi
import org.jetbrains.compose.web.testutils.runTest
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ComposeWebExperimentalTestsApi::class)
class ConfirmationHtmlContentTest {
    @Test
    fun showsTheTitleMessageAndButtons() = runTest {
        composition {
            ConfirmationHtmlContent(
                title = "Delete item?",
                message = "This cannot be undone.",
                confirmText = "Delete",
                dismissText = "Cancel",
                destructive = false,
                onConfirm = {},
                onDismiss = {},
            )
        }

        assertEquals("Delete item?", root.querySelector("h2")?.textContent)
        assertEquals("This cannot be undone.", root.querySelector("p")?.textContent)
        assertEquals(listOf("Cancel", "Delete"), root.buttons().map { it.textContent })
    }

    @Test
    fun noMessageRendersNoParagraph() = runTest {
        composition {
            ConfirmationHtmlContent(
                title = "Delete item?",
                message = null,
                confirmText = "Delete",
                dismissText = "Cancel",
                destructive = false,
                onConfirm = {},
                onDismiss = {},
            )
        }

        assertNull(root.querySelector("p"))
    }

    @Test
    fun buttonsCallTheirCallbacks() = runTest {
        var confirms = 0
        var dismisses = 0
        composition {
            ConfirmationHtmlContent(
                title = "Delete item?",
                message = null,
                confirmText = "Delete",
                dismissText = "Cancel",
                destructive = false,
                onConfirm = { confirms++ },
                onDismiss = { dismisses++ },
            )
        }

        root.button("Delete").click()
        root.button("Cancel").click()

        assertEquals(1, confirms)
        assertEquals(1, dismisses)
    }

    @Test
    fun destructiveConfirmUsesTheDestructiveClasses() = runTest {
        composition {
            ConfirmationHtmlContent(
                title = "Delete item?",
                message = null,
                confirmText = "Delete",
                dismissText = "Cancel",
                destructive = true,
                onConfirm = {},
                onDismiss = {},
            )
        }

        assertEquals("udytils-button udytils-button-destructive", root.button("Delete").className)
    }

    @Test
    fun providedClassesReplaceTheDefaults() = runTest {
        composition {
            CompositionLocalProvider(
                LocalUdytilsHtmlClasses provides UdytilsHtmlClasses(
                    title = listOf("app-title"),
                    confirmButton = listOf("app-button", "app-button-primary"),
                ),
            ) {
                ConfirmationHtmlContent(
                    title = "Delete item?",
                    message = null,
                    confirmText = "Delete",
                    dismissText = "Cancel",
                    destructive = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        assertEquals("app-title", (root.querySelector("h2") as HTMLElement).className)
        assertEquals("app-button app-button-primary", root.button("Delete").className)
        assertEquals("udytils-button udytils-button-dismiss", root.button("Cancel").className)
    }
}

internal fun HTMLElement.buttons(): List<HTMLButtonElement> =
    querySelectorAll("button").asList().map { it as HTMLButtonElement }

internal fun HTMLElement.button(text: String): HTMLButtonElement =
    buttons().single { it.textContent == text }
