package dev.isaacudy.udytils.snapshot

import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * One discovered `@Preview` plus the golden path it will be snapshotted to.
 *
 * [toString] is the JUnit parameterized display name (`feature.ukpt.ui.UkptScreenPreview`), which
 * deliberately matches the golden path identity so a failing test names the file to look at.
 */
class PreviewSnapshotCase internal constructor(
    /** The discovered preview; invoking it composes the `@Preview` function. */
    val preview: ComposablePreview<AndroidPreviewInfo>,
    /** POSIX-relative golden path under `snapshots/images/`, without the `.png` extension. */
    val goldenPath: String,
    private val displayName: String,
) {
    override fun toString(): String = displayName
}

/**
 * Discovers `@Preview` composables on the test classpath and turns them into snapshot cases.
 *
 * This is the whole per-module surface of the preview-driven pipeline: a consuming module says
 * which package tree is theirs, and everything else — golden layout, collision checking, device
 * and rendering configuration — comes from the library. See [PreviewSnapshotTestCase] for the
 * test class that consumes these.
 */
object PreviewSnapshots {

    /**
     * Scans [packageTrees] for `@Preview` composables (including private ones) and resolves each to
     * a [PreviewSnapshotCase].
     *
     * Previews are read from the compiled classes at test time, so adding a `@Preview` to a
     * composable is all that is needed to snapshot it — there is no hand-written test per state.
     *
     * Fails fast if two discovered previews resolve to the same golden path; see
     * [assertNoGoldenPathCollisions].
     */
    fun scan(vararg packageTrees: String): List<PreviewSnapshotCase> = of(
        AndroidComposablePreviewScanner()
            .scanPackageTrees(*packageTrees)
            .includePrivatePreviews()
            .getPreviews(),
    )

    /**
     * The escape hatch behind [scan]: resolves golden paths for an already-discovered list of
     * previews. Use it when the scan itself needs configuring (extra filters, annotation includes,
     * a non-default source set) but the golden layout and collision guarantees should stay the
     * library's.
     */
    fun of(previews: List<ComposablePreview<AndroidPreviewInfo>>): List<PreviewSnapshotCase> =
        previews.map { it.toSnapshotCase() }.also { assertNoGoldenPathCollisions(it) }
}

/**
 * Resolves a discovered preview's directory-grouped golden path and display name.
 *
 * - Package directories come from the preview's declaring class (e.g. `feature.ukpt.ui.UkptScreenKt`
 *   → `feature/ukpt/ui`), dropping the synthetic file class (`…ScreenKt`) — the package plus the
 *   function name is the identity.
 * - The file base is the preview's function name plus, for any non-default `@Preview` qualifiers (a
 *   `name` argument, `fontScale`, `uiMode`, `device`, …), the suffix
 *   [AndroidPreviewScreenshotIdBuilder] renders — so several `@Preview`s stacked on one function
 *   stay distinct. `ignoreClassName()` keeps just `<function>[.<qualifiers>]`.
 */
internal fun ComposablePreview<AndroidPreviewInfo>.toSnapshotCase(): PreviewSnapshotCase {
    val packageName = declaringClass.substringBeforeLast('.', missingDelimiterValue = "")
    val packageDirs = packageName.replace('.', '/')
    val fileBase = AndroidPreviewScreenshotIdBuilder(this)
        .ignoreClassName()
        .build()
    return PreviewSnapshotCase(
        preview = this,
        goldenPath = if (packageDirs.isEmpty()) fileBase else "$packageDirs/$fileBase",
        displayName = if (packageName.isEmpty()) fileBase else "$packageName.$fileBase",
    )
}

/** A resolved golden path plus the `<declaringClass>#<function>` that claimed it. */
internal data class GoldenPathClaim(val path: String, val source: String)

/**
 * Fails fast if two discovered previews map to the same golden path, naming every colliding preview
 * so it can be renamed.
 *
 * This is a correctness guard, not a nicety: two previews sharing a golden path would take turns
 * overwriting one another's PNG, so one of them would be recorded and then verified against the
 * other's render — a test that passes while covering nothing. Raising it at parameter-creation time
 * means the whole class fails once with an actionable message instead of producing a flapping golden.
 */
internal fun assertNoGoldenPathCollisions(cases: List<PreviewSnapshotCase>) {
    val message = goldenPathCollisionMessage(
        cases.map {
            GoldenPathClaim(
                path = it.goldenPath,
                source = "${it.preview.declaringClass}#${it.preview.methodName}",
            )
        },
    )
    if (message != null) error(message)
}

/**
 * The pure half of [assertNoGoldenPathCollisions]: returns the failure message when any path is
 * claimed more than once, or `null` when every claim is unique.
 */
internal fun goldenPathCollisionMessage(claims: List<GoldenPathClaim>): String? {
    val collisions = claims
        .groupBy { it.path }
        .filterValues { it.size > 1 }
    if (collisions.isEmpty()) return null
    val detail = collisions.entries.joinToString("\n") { (path, colliding) ->
        "  $path.png <- ${colliding.joinToString(", ") { it.source }}"
    }
    return "Preview snapshot golden-path collision: ${collisions.size} path(s) claimed by more than " +
        "one @Preview. Rename the colliding preview function(s):\n$detail"
}
