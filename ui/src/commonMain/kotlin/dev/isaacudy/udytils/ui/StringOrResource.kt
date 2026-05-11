package dev.isaacudy.udytils.ui

import androidx.compose.runtime.Composable
import dev.isaacudy.udytils.UdytilsResources
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.stringResource

typealias StringOrResource = dev.isaacudy.udytils.string.StringOrResource

@OptIn(InternalResourceApi::class)
@Composable
fun StringOrResource.asString(): String {
    val string = string
    if (string != null) return string
    val key = requireNotNull(resourceKey)

    val resource = UdytilsResources.stringResources[key]
        ?: error("StringResource with key $key not found. Make sure you have added your StringResources to UdytilsResources using UdytilsResources.registerResources")
    return stringResource(resource)
}
