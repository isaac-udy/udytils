package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider

/**
 * The production and test scopes of one run, plus an index of production declarations so a rule
 * over test code can ask which production Construct a referenced declaration belongs to.
 */
class ArchitectureScopes internal constructor(
    productionProvider: () -> KoScope,
    testProvider: (() -> KoScope)?,
    private val productionConstructs: List<Construct<*>>,
) {
    val production: KoScope by lazy { productionProvider() }

    val tests: KoScope by lazy {
        requireNotNull(testProvider) { "a rule reads the test scope, but the ArchitectureDefinition declares no testScope" }()
    }

    fun of(codeScope: CodeScope): KoScope = when (codeScope) {
        CodeScope.Production -> production
        CodeScope.Test -> tests
    }

    /** One production declaration a file refers to, with the production Constructs it matches. */
    data class Reference(
        val name: String,
        val declaration: KoBaseDeclaration,
        val constructs: List<Construct<*>>,
    )

    private val byFqn: Map<String, List<KoBaseDeclaration>> by lazy {
        production.constructCandidates()
            .mapNotNull { decl -> (decl as? KoFullyQualifiedNameProvider)?.fullyQualifiedName?.let { it to decl } }
            .groupBy({ it.first }, { it.second })
    }

    private val byPackage: Map<String, List<KoBaseDeclaration>> by lazy {
        byFqn.entries
            .groupBy({ it.key.substringBeforeLast('.', "") }, { it.value })
            .mapValues { (_, lists) -> lists.flatten() }
    }

    /** The production declarations matching [fqn], or the declarations enclosing it (a nested type or member). */
    fun resolveProduction(fqn: String): List<KoBaseDeclaration> {
        var candidate = fqn
        while (candidate.isNotEmpty()) {
            byFqn[candidate]?.let { return it }
            candidate = candidate.substringBeforeLast('.', "")
        }
        return emptyList()
    }

    /** The production Constructs [declaration] matches. */
    fun constructsOf(declaration: KoBaseDeclaration): List<Construct<*>> =
        productionConstructs.filter { it.test(declaration) }

    /**
     * Production declarations [file] refers to: explicit imports, plus same-package and wildcard-import
     * declarations whose simple name occurs in the file's text, which Kotlin resolves without an import.
     */
    fun productionReferences(file: KoFileDeclaration): List<Reference> {
        val ownPackage = file.packagee?.name.orEmpty()
        val wildcardPackages = file.imports.filter { it.isWildcard }.map { it.name.removeSuffix(".*") }
        val explicit = file.imports.filterNot { it.isWildcard }.flatMap { import -> resolveProduction(import.name) }
        val identifiers = IDENTIFIER.findAll(file.textWithoutHeader()).map { it.value }.toSet()
        val implicit = (wildcardPackages + ownPackage).distinct().flatMap { pkg ->
            byPackage[pkg].orEmpty().filter { (it as? KoNameProvider)?.name in identifiers }
        }
        return (explicit + implicit)
            .distinct()
            .map { decl -> Reference((decl as? KoNameProvider)?.name ?: decl.toString(), decl, constructsOf(decl)) }
    }
}

private val IDENTIFIER = Regex("""\b[A-Za-z_][A-Za-z0-9_]*\b""")

private fun KoFileDeclaration.textWithoutHeader(): String =
    text.lineSequence()
        .filterNot { line -> line.trimStart().let { it.startsWith("package ") || it.startsWith("import ") } }
        .joinToString("\n")
