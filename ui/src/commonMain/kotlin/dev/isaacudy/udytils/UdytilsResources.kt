package dev.isaacudy.udytils

import dev.isaacudy.udytils.ui.generated.resources.Res
import dev.isaacudy.udytils.ui.generated.resources.allStringResources
import org.jetbrains.compose.resources.StringResource

object UdytilsResources {
    internal val stringResources = mutableMapOf<String, StringResource>()
        .apply { putAll(Res.allStringResources) }

    fun registerResources(
        strings: Map<String, StringResource>
    ) {
        val duplicates = stringResources.keys.intersect(strings.keys)
        if (duplicates.isNotEmpty()) {
            error("Duplicate string resources found: ${duplicates.joinToString(", ") { "\"$it\"" }}")
        }
        stringResources.putAll(strings)
    }
}