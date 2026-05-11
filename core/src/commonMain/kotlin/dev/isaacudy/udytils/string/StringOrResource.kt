package dev.isaacudy.udytils.string

import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

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
