package dev.isaacudy.udytils.string

import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * Holds either a literal [string] or the [resourceKey] of a compose-resources [StringResource] —
 * exactly one of the two is always non-null.
 *
 * Lets non-UI code (error messages, navigation keys, serialized payloads) carry text that may be
 * localised without depending on a Compose context. Resolve to display text with the ui module's
 * `StringOrResource.asString()` composable extension.
 */
@Serializable
@ConsistentCopyVisibility
data class StringOrResource private constructor(
    val string: String?,
    val resourceKey: String?,
) {
    init {
        if (string == null && resourceKey == null) {
            error("StringOrResource must contain either a String or a resourceKey")
        }
        if (string != null && resourceKey != null) {
            error("StringOrResource must contain either a String or a resourceKey, not both")
        }
    }

    constructor(
        string: String
    ) : this(
        string = string,
        resourceKey = null,
    )

    constructor(
        resource: StringResource,
    ) : this(
        string = null,
        resourceKey = resource.key,
    )
}
