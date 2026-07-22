package dev.isaacudy.udytils.snapshot

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.Navigation
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen

/**
 * The rendering defaults every snapshot in this harness uses unless a test says otherwise.
 *
 * These values exist as a named, documented object rather than as bare default arguments because
 * they were the thing that drifted when the harness was copied per module: the same `screen()`
 * helper ended up on [RenderingMode.SHRINK] in one project and [RenderingMode.V_SCROLL] in another,
 * with no record of which was intended. Each choice below states its reason, and every one of them
 * is overridable per test — see the individual properties.
 */
object SnapshotDefaults {

    /**
     * A large, high-density, square canvas: 1920x1920 px at 320 dpi, i.e. **960 x 960 dp**.
     *
     * This is a deliberately synthetic device rather than a real phone profile. Nothing in the
     * harness renders *at* the device size — [SnapshotRule.screen] and [SnapshotRule.component]
     * bound layout with their own root container, and the preview-driven pipeline lets content
     * grow past the viewport (see [previewRenderingMode]). The device only has to be big enough
     * not to be the constraint, and square so that a test can pick either orientation without
     * changing devices.
     *
     * Note that Paparazzi scales the emitted PNG down to roughly 1000 px on its long side, so
     * ~960 dp per axis is the practical ceiling for a single golden; content beyond it is not
     * worth rendering here — split the test instead.
     *
     * Everything is set to the "no hardware" option (no keyboard, no touchscreen, no soft buttons,
     * no navigation) so nothing device-shaped leaks into the render.
     *
     * To snapshot at a real device profile instead, pass any Paparazzi [DeviceConfig] — including
     * the stock ones such as `DeviceConfig.PIXEL_5` — to [SnapshotRule] or to
     * [PreviewSnapshotTestCase]'s `deviceConfig` parameter.
     */
    val deviceConfig: DeviceConfig = DeviceConfig(
        screenHeight = 1920,
        screenWidth = 1920,
        xdpi = 320,
        ydpi = 320,
        orientation = ScreenOrientation.LANDSCAPE,
        density = Density.create(320),
        ratio = ScreenRatio.LONG,
        size = ScreenSize.NORMAL,
        keyboard = Keyboard.NOKEY,
        touchScreen = TouchScreen.NOTOUCH,
        keyboardState = KeyboardState.HIDDEN,
        softButtons = false,
        navigation = Navigation.NONAV,
    )

    /**
     * [RenderingMode.SHRINK] — the rendering mode for [SnapshotRule.screen].
     *
     * `screen()` wraps the composable under test in a root container of a **known, fixed size**
     * ([screenWidth] x [screenHeight] by default). That container is what gives children bounded
     * constraints, so `fillMaxWidth()` / `fillMaxSize()` resolve correctly either way; the
     * rendering mode only decides how much canvas ends up in the PNG.
     *
     * `SHRINK` crops the golden to the container. `V_SCROLL` would instead emit the full device
     * width, so any test that renders narrower than [deviceConfig] — a phone-width surface on this
     * 960 dp canvas, say — gets a wide dead border around the content it actually cares about,
     * which both wastes pixels and dilutes the diff percentage that decides pass/fail. At the
     * default 960 x 960 dp the two modes produce the same image, so choosing `SHRINK` costs
     * nothing and fixes the narrow case.
     *
     * The trade-off: `SHRINK` only ever shrinks, so content that overflows the container is
     * clipped rather than expanding the image. That is the right behaviour here — the container
     * size is explicit, so overflowing it is a layout result the test author chose to bound. Where
     * there is no such container, prefer [previewRenderingMode].
     *
     * Override per call: `snapshot.screen(renderingMode = RenderingMode.V_SCROLL) { … }`.
     */
    val screenRenderingMode: RenderingMode = RenderingMode.SHRINK

    /**
     * [RenderingMode.SHRINK] — the rendering mode for [SnapshotRule.component].
     *
     * A component snapshot is a tight crop of one self-sizing composable plus a small margin, so
     * shrink-wrapping is the whole point: the golden is dense with the thing under test, and a
     * pixel diff over it is meaningful rather than being averaged away across empty canvas.
     */
    val componentRenderingMode: RenderingMode = RenderingMode.SHRINK

    /**
     * [RenderingMode.V_SCROLL] — the rendering mode for the preview-driven [PreviewSnapshotTestCase].
     *
     * Unlike [SnapshotRule], the preview pipeline renders each discovered `@Preview` **directly**,
     * with no fixed-size wrapper — the previews are arbitrary and the harness has no idea whether
     * the next one is a chip or a full scrolling screen. `V_SCROLL` measures with an unbounded
     * height and grows the image to fit, so a preview taller than the viewport is captured in full.
     *
     * With `SHRINK` (which never expands) that same preview would be silently cropped at the
     * bottom, and the golden would keep passing while the content below the fold went unreviewed.
     * Losing coverage silently is worse than a tall PNG, so the preview pipeline expands.
     *
     * Override per test class via [PreviewSnapshotTestCase]'s `renderingMode` parameter.
     */
    val previewRenderingMode: RenderingMode = RenderingMode.V_SCROLL

    /** Default width of [SnapshotRule.screen]'s root container: the full [deviceConfig] width. */
    val screenWidth: Dp = 960.dp

    /** Default height of [SnapshotRule.screen]'s root container: the full [deviceConfig] height. */
    val screenHeight: Dp = 960.dp

    /** Default breathing margin drawn around a [SnapshotRule.component] render. */
    val componentPadding: Dp = 8.dp
}
