package dev.isaacudy.udytils.rules

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import dev.isaacudy.udytils.architecture.Violation

/**
 * The udytils package roots used to spell the family boundaries. Modules are attributed by file
 * path (not package) because packages are not unique per module — ui declares `ViewModelState`
 * inside core's `dev.isaacudy.udytils.state` package — but *imports* can only be judged by name,
 * so the boundaries are expressed as "files of family A must not import packages of family B"
 * using each family's unambiguous package roots.
 */
internal object Packages {
    const val UDYTILS = "dev.isaacudy.udytils."
    const val URPC = "dev.isaacudy.udytils.urpc."
    const val POSTGRES = "dev.isaacudy.udytils.postgres."
    const val ARCHITECTURE = "dev.isaacudy.udytils.architecture."

    /** Package roots only the ui module declares. */
    val UI = listOf(
        "dev.isaacudy.udytils.ui.",
        "dev.isaacudy.udytils.permissions.",
        "dev.isaacudy.udytils.android.",
    )
}

/**
 * Violations for every import whose containing file matches [inFiles] (normalised repo path,
 * forward slashes) and whose imported name starts with one of [forbidden] — unless it also starts
 * with one of [allowed]. The `exempt` predicate is the runner's, so a file-level
 * `@ArchitectureException` on the importing file is honoured.
 */
internal fun forbiddenImports(
    scope: KoScope,
    exempt: (KoBaseDeclaration) -> Boolean,
    inFiles: (String) -> Boolean,
    forbidden: List<String>,
    allowed: List<String> = emptyList(),
    because: String,
): List<Violation> =
    scope.files
        .filter { inFiles(it.path.replace('\\', '/')) }
        .flatMap { it.imports }
        .filterNot(exempt)
        .filter { import ->
            forbidden.any { import.name.startsWith(it) } &&
                allowed.none { import.name.startsWith(it) }
        }
        .map { import -> Violation(import, "imports ${import.name} — $because") }
