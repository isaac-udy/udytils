package dev.isaacudy.udytils.snapshot

import app.cash.paparazzi.DeviceConfig
import com.android.resources.Density
import com.android.resources.ScreenOrientation

/**
 * Parses a `@Preview(device = "spec:width=1080px,height=2400px,dpi=440")` string into a Paparazzi
 * [DeviceConfig] whose pixel canvas equals the requested `width x height` when rendered in
 * `RenderingMode.NORMAL` with `useDeviceResolution = true` (see [PreviewSnapshotTestCase]'s
 * `honorSpecDevices`).
 *
 * Returns `null` for a blank, absent, or unparseable device string — which is how a preview is
 * routed to the default-canvas rendering path rather than the true-resolution one. `width` and
 * `height` accept a bare number or a `px` suffix; `dpi` defaults to 160 (mdpi) when omitted, and
 * orientation follows the aspect ratio.
 *
 * Only the `spec:` device language is understood — not the named-device form
 * (`@Preview(device = Devices.PIXEL_5)`), which resolves to an `id:`/`name:` string this returns
 * `null` for. Pass such a device through [PreviewSnapshotTestCase]'s `deviceConfig` parameter instead.
 */
internal fun deviceConfigFromSpec(device: String): DeviceConfig? {
    if (!device.startsWith("spec:")) return null
    val values = device.removePrefix("spec:")
        .split(',')
        .mapNotNull { entry ->
            val (key, value) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            key.trim() to value.trim()
        }
        .toMap()

    fun px(key: String): Int? = values[key]?.removeSuffix("px")?.trim()?.toIntOrNull()

    val width = px("width") ?: return null
    val height = px("height") ?: return null
    val dpi = values["dpi"]?.toIntOrNull() ?: 160

    return DeviceConfig(
        screenWidth = width,
        screenHeight = height,
        xdpi = dpi,
        ydpi = dpi,
        density = Density.create(dpi),
        orientation = if (height >= width) ScreenOrientation.PORTRAIT else ScreenOrientation.LANDSCAPE,
    )
}
