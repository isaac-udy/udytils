# Udytils Snapshot

A [Paparazzi](https://github.com/cashapp/paparazzi) snapshot-testing harness for Compose UI:
`@Preview`-driven test discovery, directory-grouped golden images, a golden-path collision
guard, and one documented set of device/rendering defaults.

**Artifact:** `dev.isaacudy.udytils:snapshot` · **Scope:** Android host tests
(`androidHostTest` / `testImplementation`)

```kotlin
plugins {
    alias(libs.plugins.paparazzi)   // required — see "What the Paparazzi plugin supplies"
}

dependencies {
    // KMP android library:
    // getByName("androidHostTest").dependencies { implementation("dev.isaacudy.udytils:snapshot:<version>") }
    testImplementation("dev.isaacudy.udytils:snapshot:<version>")
}
```

## Why this exists

The harness this module contains started life as four files copied into every client module of
every project that wanted snapshot coverage. Copies drift: the same `SnapshotRule.screen()`
helper ended up on `RenderingMode.SHRINK` in one project and `RenderingMode.V_SCROLL` in
another, with the reasoning recorded in one copy's comments and lost from the rest. Everything
in those files is generic; the only genuinely per-module fact is *which package tree holds this
module's previews*. So it is an artifact, and a consuming module now writes six lines.

## Preview-driven snapshots

Subclass [`PreviewSnapshotTestCase`](src/main/kotlin/dev/isaacudy/udytils/snapshot/PreviewSnapshotTestCase.kt)
and name the package tree to scan. That is the whole per-module surface:

```kotlin
class PreviewSnapshotTest(case: PreviewSnapshotCase) : PreviewSnapshotTestCase(case) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<PreviewSnapshotCase> = PreviewSnapshots.scan("feature.ukpt")
    }
}
```

Previews are discovered from the compiled classes at test time
([ComposablePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner)), so
adding a `@Preview` to a composable is all it takes to snapshot it — there is no hand-written
test per state. `@RunWith(Parameterized::class)` is inherited from the base class;
`@Parameterized.Parameters` is the one thing JUnit 4 insists on reading from the concrete
class, which is why the companion stays yours.

Record and verify with the Paparazzi plugin's tasks (both need `--no-configuration-cache`,
because under the configuration cache the R class is dropped from the test runtime classpath):

```
./gradlew :feature:core:client:recordPaparazzi --no-configuration-cache
./gradlew :feature:core:client:verifyPaparazzi --no-configuration-cache
```

### Directory-grouped goldens

Goldens are grouped by the preview's declaring package and function name, so a preview in
`feature.ukpt.ui` lands at `snapshots/images/feature/ukpt/ui/UkptScreenPreview.png` instead of
stock Paparazzi's single long flat filename. Several `@Preview`s stacked on one function stay
distinct through the qualifier suffix (`name`, `fontScale`, `uiMode`, `device`, …).

[`DirectorySnapshotHandler`](src/main/kotlin/dev/isaacudy/udytils/snapshot/DirectorySnapshotHandler.kt)
implements that layout. It is a custom Paparazzi `SnapshotHandler` because the stock
`HtmlReportWriter` and `SnapshotVerifier` both derive the golden path from `Snapshot.toFileName`,
which joins with `_` and rejects `/`. It otherwise mirrors stock behaviour exactly — the same
report/record/verify modes, the same `OffByTwo` pixel diff, threshold and size tolerance.

### Golden-path collisions fail the build

If two discovered previews resolve to the same golden path they would take turns overwriting
one another's PNG — one would be recorded and then verified against the other's render, and the
test would pass while covering nothing. `PreviewSnapshots.scan` raises that at
parameter-creation time, naming every claimant:

```
Preview snapshot golden-path collision: 1 path(s) claimed by more than one @Preview.
Rename the colliding preview function(s):
  feature/ukpt/ui/Preview.png <- feature.ukpt.ui.AKt#Preview, feature.ukpt.ui.BKt#Preview
```

### Store/marketing shots at true resolution

The default pipeline renders every preview on one fixed canvas and lets Paparazzi thumbnail-scale
the golden down to a ~1000 px longest edge — right for regression coverage, wrong for a store
listing that must be an exact `1080 x 2400`. Pin the size on the `@Preview` and set
`honorSpecDevices = true`:

```kotlin
@Preview(device = "spec:width=1080px,height=2400px,dpi=440")
@Composable private fun ProjectsHeroMarketing() = MarketingScreen(...)

// Marketing: render spec-device previews at their true pixel size.
class MarketingSnapshotTest(case: PreviewSnapshotCase) :
    PreviewSnapshotTestCase(case, honorSpecDevices = true) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = PreviewSnapshots.scan("feature.marketing").filter { it.hasSpecDevice }
    }
}

// Regression: everything else, on the default canvas.
class PreviewSnapshotTest(case: PreviewSnapshotCase) : PreviewSnapshotTestCase(case) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = PreviewSnapshots.scan("feature.marketing").filter { !it.hasSpecDevice }
    }
}
```

A `spec:` preview rendered this way uses `RenderingMode.NORMAL` with `useDeviceResolution`, so the
golden is exactly `width x height` pixels. `honorSpecDevices` only changes previews that carry a
parseable `spec:` device ([`PreviewSnapshotCase.hasSpecDevice`](src/main/kotlin/dev/isaacudy/udytils/snapshot/PreviewSnapshots.kt));
everything else renders unchanged, so it is safe on a mixed scan. The `hasSpecDevice` filter splits
one scan across the two tests so each preview is rendered exactly once. Named devices
(`@Preview(device = Devices.PIXEL_5)`) are not `spec:` devices — pass those through the
`deviceConfig` parameter instead.

## Hand-written snapshots

[`SnapshotRule`](src/main/kotlin/dev/isaacudy/udytils/snapshot/SnapshotRule.kt) is for snapshots
that are a *curated composition* rather than a regression check on a real screen — design-system
reference sheets, a labelled grid of every button variant, documentation surfaces embedded in
Markdown:

```kotlin
class ButtonSpecimenTest {
    @get:Rule val snapshot = SnapshotRule()

    @Test fun allVariants() = snapshot.screen(width = 390.dp) { ButtonSpecimenSheet() }

    @Test fun primaryButton() = snapshot.component { PrimaryButton("Continue") }
}
```

These goldens use Paparazzi's **stock flat naming** (`<package>_<Class>_<method>.png`), not the
directory-grouped layout: their names are load-bearing when documentation references them by
filename, so the name should follow the test method the author chose.

## Defaults, and how to override them

All defaults live in
[`SnapshotDefaults`](src/main/kotlin/dev/isaacudy/udytils/snapshot/SnapshotDefaults.kt), each
with its reasoning in KDoc.

| | Default | Why |
|---|---|---|
| Device | 1920x1920 px @ 320 dpi (**960 x 960 dp**), no keyboard/touch/nav | Synthetic, not a phone profile: layout is bounded by the test's own container, so the device only has to be big enough not to be the constraint. Square, so a test can pick either orientation without changing devices. |
| `SnapshotRule.screen` | `RenderingMode.SHRINK` | `screen()` already wraps content in a fixed-size container, and `SHRINK` crops the golden to it. `V_SCROLL` would pad out to the full device width, so a narrower surface gets a wide dead border that also dilutes the diff percentage. At the default 960 x 960 dp the two are identical, so this costs nothing and fixes the narrow case. |
| `SnapshotRule.component` | `RenderingMode.SHRINK` | A tight crop of the component plus an 8 dp margin; a pixel diff over it is meaningful rather than averaged away across empty canvas. |
| `PreviewSnapshotTestCase` | `RenderingMode.NORMAL` | Bounds each preview to the device canvas (960 x 960 dp) in **both** axes. A screen preview expects that, and a root `Modifier.verticalScroll(...)` renders correctly only when its height is bounded — under `V_SCROLL`'s unbounded height it measures to nothing and the frame is blank. Opt a genuinely-taller-than-viewport preview into `V_SCROLL` per test class (it grows to fit, but a *root*-scroll preview rendered that way is blank). |

This is the deliberate resolution of the drift described above: `SHRINK`/`NORMAL` where a bound
makes the render exact, `V_SCROLL` only where a preview genuinely exceeds the viewport and is opted
in. A **blank-render guard** backs this up: `recordPaparazzi` refuses to commit a frame that is a
single uniform colour (a preview that measured to nothing), failing loudly instead of silently
recording an empty golden. Disable it per test class with `guardBlankRenders = false` for a preview
that really is one solid colour.

Override per call or per test class:

```kotlin
snapshot.screen(renderingMode = RenderingMode.V_SCROLL) { … }
snapshot.component(padding = 24.dp) { … }

class WidePreviewSnapshotTest(case: PreviewSnapshotCase) : PreviewSnapshotTestCase(
    case = case,
    deviceConfig = DeviceConfig.PIXEL_5,
)
```

Note that Paparazzi scales the emitted PNG down to roughly 1000 px on its long side, so ~960 dp
per axis is the practical ceiling for one golden — split the test rather than growing the canvas.

## What the Paparazzi plugin supplies

The consuming module must apply the Paparazzi Gradle plugin. It provides the `recordPaparazzi` /
`verifyPaparazzi` tasks, the `paparazzi.*` system properties `DirectorySnapshotHandler` reads,
and the Paparazzi runtime itself with its matching layoutlib native artifacts.

That is why this artifact declares Paparazzi as `compileOnly` rather than `api`: publishing our
own version would let the runtime drift from the plugin that drives it, which fails as a
layoutlib mismatch rather than at dependency resolution. Compose is `compileOnly` for the same
reason — a snapshot test renders the *consumer's* Compose, and pinning ours could silently move
their host-test classpath off the version their app ships.

ComposablePreviewScanner and JUnit 4 **are** exposed as `api` dependencies, so a consuming
host-test source set gets them from this artifact without re-declaring them.

## Not a KMP module

Unlike `core` and `ui` this is a plain `com.android.library`. Paparazzi renders through
layoutlib against the Android Compose artifacts and only ever runs from an Android host-test
source set, so there is no second platform to target; a KMP wrapper around a single Android
target would be ceremony with no consumer benefit.
