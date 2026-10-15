package dev.isaacudy.udytils.ui.html

import androidx.compose.runtime.CompositionLocalProvider
import dev.isaacudy.udytils.ui.StringOrResource
import dev.isaacudy.udytils.ui.destinations.ConfirmationDestination
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.testutils.ComposeWebExperimentalTestsApi
import org.jetbrains.compose.web.testutils.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ComposeWebExperimentalTestsApi::class)
class HtmlStringResolverTest {
    private val deleteTitle = Json.decodeFromString<StringOrResource>(
        """{"string":null,"resourceKey":"delete_title"}""",
    )

    @Test
    fun aStringIsItsOwnText() = runTest {
        composition { P { Text(StringOrResource("Delete item?").asHtmlString()) } }

        assertEquals("Delete item?", root.textContent)
    }

    @Test
    fun aResourceKeyReadsTheProvidedResolver() = runTest {
        composition {
            CompositionLocalProvider(
                LocalHtmlStringResolver provides HtmlStringResolver { key ->
                    if (key == "delete_title") "Supprimer ?" else null
                },
            ) {
                P { Text(deleteTitle.asHtmlString()) }
            }
        }

        assertEquals("Supprimer ?", root.textContent)
    }

    @Test
    fun theUiDefaultsFallBackToEnglish() = runTest {
        val key = ConfirmationDestination(title = "Delete item?")
        composition {
            P { Text(key.confirmText.asHtmlString()) }
            P { Text(key.dismissText.asHtmlString()) }
        }

        assertEquals("ConfirmClose", root.textContent)
    }

    @Test
    fun theResolverOverridesTheUiDefaults() = runTest {
        val key = ConfirmationDestination(title = "Delete item?")
        composition {
            CompositionLocalProvider(
                LocalHtmlStringResolver provides HtmlStringResolver { resourceKey -> if (resourceKey == "confirm") "Valider" else null },
            ) {
                P { Text(key.confirmText.asHtmlString()) }
            }
        }

        assertEquals("Valider", root.textContent)
    }
}
