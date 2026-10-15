package dev.isaacudy.udytils.ui.html

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.isaacudy.udytils.ui.StringOrResource

/**
 * Text for the resource keys in [StringOrResource]s. Compose HTML can't read Compose resources,
 * so an app whose keys or errors carry a [org.jetbrains.compose.resources.StringResource]
 * provides the text through [LocalHtmlStringResolver]. Returns null for a key it doesn't hold.
 */
fun interface HtmlStringResolver {
    fun resolve(resourceKey: String): String?
}

val LocalHtmlStringResolver = staticCompositionLocalOf { HtmlStringResolver { null } }

/**
 * The text of this [StringOrResource]: the string, or the [LocalHtmlStringResolver] text for its
 * resource key. The keys of the ui module's own defaults ("confirm", "close") fall back to English.
 */
@Composable
fun StringOrResource.asHtmlString(): String {
    string?.let { return it }
    val key = requireNotNull(resourceKey)
    return LocalHtmlStringResolver.current.resolve(key)
        ?: uiDefaultStrings[key]
        ?: error("No text for resource key \"$key\". Provide it through LocalHtmlStringResolver.")
}

private val uiDefaultStrings = mapOf(
    "confirm" to "Confirm",
    "close" to "Close",
)
