package dev.isaacudy.udytils.snapshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.rules.TestRule

/**
 * JUnit rule wrapping Paparazzi for **hand-written** snapshot tests of Compose UI, with no device.
 *
 * - [screen] renders inside a fixed-size container (bounded constraints) — use it for screen content
 *   and any composable that uses `fillMaxWidth()` / `fillMaxSize()`.
 * - [component] renders at content size with a small margin — use it for small, self-sizing
 *   composables.
 *
 * Use this when the snapshot is a **curated composition** rather than a regression check on a real
 * screen: design-system reference sheets, a labelled grid of every button variant, a documentation
 * surface embedded in Markdown. For ordinary per-screen regression coverage prefer the
 * preview-driven [PreviewSnapshotTestCase], which needs no test written at all.
 *
 * Goldens use Paparazzi's **stock flat naming** (`<package>_<Class>_<method>.png`), not the
 * directory-grouped layout of [DirectorySnapshotHandler]. That is deliberate: these tests are
 * authored by hand and their goldens are often referenced from documentation by filename, so the
 * name should follow the test method the author chose. Renaming a test method renames its golden.
 *
 * Composables under test must be `internal` (not `private`) so the host-test source set can reach
 * them. The consuming module must apply the Paparazzi Gradle plugin.
 *
 * @param deviceConfig the canvas to render on; defaults to [SnapshotDefaults.deviceConfig]. Layout
 *   is bounded by [screen]/[component]'s own root container, so this only needs to be at least as
 *   large as the largest surface under test.
 */
class SnapshotRule private constructor(
    private val paparazzi: Paparazzi,
    private val deviceConfig: DeviceConfig,
) : TestRule by paparazzi {

    constructor(deviceConfig: DeviceConfig = SnapshotDefaults.deviceConfig) : this(
        paparazzi = Paparazzi(deviceConfig = deviceConfig),
        deviceConfig = deviceConfig,
    )

    /**
     * Renders [composable] inside a root container of exactly [width] x [height] and snapshots it.
     *
     * The container is what gives children bounded constraints, so `fillMaxWidth()` and friends
     * resolve against [width]/[height] rather than the device. Pass a narrower [width] to snapshot
     * a phone-shaped surface on the default large canvas.
     *
     * @param renderingMode defaults to [SnapshotDefaults.screenRenderingMode] ([RenderingMode.SHRINK]),
     *   which crops the golden to the container instead of padding it out to the full device width.
     *   Content that overflows the container is therefore clipped; pass [RenderingMode.V_SCROLL] if
     *   a test needs the image to grow instead.
     */
    fun screen(
        name: String? = null,
        width: Dp = SnapshotDefaults.screenWidth,
        height: Dp = SnapshotDefaults.screenHeight,
        renderingMode: RenderingMode = SnapshotDefaults.screenRenderingMode,
        composable: @Composable () -> Unit,
    ) {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = deviceConfig,
            renderingMode = renderingMode,
        )
        paparazzi.snapshot(name) {
            Box(modifier = Modifier.width(width).height(height)) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    composable()
                }
            }
        }
    }

    /**
     * Renders [composable] at its own content size with [padding] around it, and snapshots it.
     *
     * @param renderingMode defaults to [SnapshotDefaults.componentRenderingMode]
     *   ([RenderingMode.SHRINK]) so the golden is a tight crop of the component under test.
     */
    fun component(
        name: String? = null,
        padding: Dp = SnapshotDefaults.componentPadding,
        renderingMode: RenderingMode = SnapshotDefaults.componentRenderingMode,
        composable: @Composable () -> Unit,
    ) {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = deviceConfig,
            renderingMode = renderingMode,
        )
        paparazzi.snapshot(name) {
            Box(modifier = Modifier.padding(padding)) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    composable()
                }
            }
        }
    }
}
