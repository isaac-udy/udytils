package dev.isaacudy.udytils.snapshot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Snapshot-tests every `@Preview` composable in a module, one JUnit case per preview.
 *
 * Subclass it and supply the package tree to scan — that is the entire per-module surface:
 *
 * ```kotlin
 * class PreviewSnapshotTest(case: PreviewSnapshotCase) : PreviewSnapshotTestCase(case) {
 *     companion object {
 *         @JvmStatic
 *         @Parameterized.Parameters(name = "{0}")
 *         fun cases(): List<PreviewSnapshotCase> = PreviewSnapshots.scan("feature.ukpt")
 *     }
 * }
 * ```
 *
 * `@RunWith(Parameterized::class)` is inherited from this class, and `@Parameterized.Parameters` is
 * the one thing JUnit 4 insists on reading from the concrete class, which is why the companion is
 * yours rather than the library's.
 *
 * Previews are discovered from the compiled classes at test time, so adding a `@Preview` to a
 * composable is all it takes to snapshot it — there is no hand-written test per state. Goldens are
 * directory-grouped by the preview's declaring package and function name (see
 * [DirectorySnapshotHandler]), e.g. `snapshots/images/feature/ukpt/ui/UkptScreenPreview.png`.
 *
 * The consuming module must apply the Paparazzi Gradle plugin: it supplies the Paparazzi runtime
 * this harness compiles against, the `recordPaparazzi` / `verifyPaparazzi` tasks, and the
 * `paparazzi.*` system properties [DirectorySnapshotHandler] reads. Both tasks need
 * `--no-configuration-cache`.
 *
 * @param case the preview to render, supplied by JUnit from the `@Parameters` method.
 * @param deviceConfig the canvas to render on; defaults to [SnapshotDefaults.deviceConfig].
 * @param renderingMode how the canvas is sized to the content; defaults to
 *   [SnapshotDefaults.previewRenderingMode] ([RenderingMode.NORMAL]), which bounds the preview to the
 *   [deviceConfig] canvas so a root `Modifier.verticalScroll` renders correctly. Pass
 *   [RenderingMode.V_SCROLL] for a preview that must grow beyond the viewport — but not one whose
 *   root scrolls, which renders blank under an unbounded height. Read that property's documentation
 *   before overriding it.
 * @param guardBlankRenders when true (the default), recording a frame that is a single flat colour
 *   fails instead of committing that blank as the golden (see [DirectorySnapshotHandler]). Set it
 *   false only for a preview that is genuinely one solid colour.
 * @param honorSpecDevices when true, a case whose `@Preview` pins a `spec:` device (see
 *   [PreviewSnapshotCase.specDeviceConfig]) is rendered at that device's **true pixel resolution**
 *   — its exact `width x height`, in `RenderingMode.NORMAL` with `useDeviceResolution`, bypassing
 *   [deviceConfig]/[renderingMode] and Paparazzi's usual thumbnail downscale — which is what a
 *   store/marketing shot needs. Cases without a `spec:` device are unaffected, so this is safe to
 *   set on a mixed scan; the default is false, leaving every existing golden untouched. Pair it
 *   with a [PreviewSnapshotCase.hasSpecDevice] filter so marketing and regression previews are each
 *   rendered by exactly one test.
 */
@RunWith(Parameterized::class)
abstract class PreviewSnapshotTestCase(
    private val case: PreviewSnapshotCase,
    deviceConfig: DeviceConfig = SnapshotDefaults.deviceConfig,
    renderingMode: RenderingMode = SnapshotDefaults.previewRenderingMode,
    guardBlankRenders: Boolean = true,
    honorSpecDevices: Boolean = false,
) {

    @get:Rule
    val paparazzi: Paparazzi = when (val specConfig = if (honorSpecDevices) case.specDeviceConfig else null) {
        // A @Preview(device = "spec:…") rendered at its true pixel size for store/marketing shots.
        null -> Paparazzi(
            deviceConfig = deviceConfig,
            renderingMode = renderingMode,
            snapshotHandler = DirectorySnapshotHandler(failOnUniformRender = guardBlankRenders),
        )
        else -> Paparazzi(
            deviceConfig = specConfig,
            renderingMode = RenderingMode.NORMAL,
            snapshotHandler = DirectorySnapshotHandler(failOnUniformRender = guardBlankRenders),
            useDeviceResolution = true,
        )
    }

    @Test
    fun snapshot() {
        paparazzi.snapshot(name = case.goldenPath) {
            // Previews are authored to render in inspection mode (the IDE preview pane sets it), so
            // anything gated on LocalInspectionMode draws its preview-safe branch here too.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                case.preview()
            }
        }
    }
}
