package dev.isaacudy.udytils.ui

import androidx.compose.runtime.Composable
import dev.isaacudy.udytils.UdytilsResources
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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

@OptIn(InternalResourceApi::class)
@Composable
fun StringOrResource.asString(): String {
    if (string != null) return string
    val key = requireNotNull(resourceKey)

    val resource = UdytilsResources.stringResources[key]
        ?: error("StringResource with key $key not found. Make sure you have added your StringResources to UdytilsResources using UdytilsResources.registerResources")
    return stringResource(resource)
}
