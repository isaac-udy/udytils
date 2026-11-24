package dev.isaacudy.udytils.architecture

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider
import com.lemonappdev.konsist.api.provider.KoTextProvider

/**
 * One classifiable declaration and the Constructs it matches. [path] is relative to the project
 * root; [startLine] and [endLine] are 1-based and inclusive. [constructs] is empty for a declaration
 * that matches no Construct and holds several for one that matches more than one.
 */
data class ClassifiedDeclaration(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val name: String,
    val codeScope: CodeScope,
    val constructs: List<Construct<*>>,
)

/**
 * Every classifiable declaration of the run's production scope, and of its test scope when the run
 * has one, matched against the Constructs of the same [CodeScope].
 */
fun classify(run: ArchitectureRun): List<ClassifiedDeclaration> {
    val codeScopes = listOf(CodeScope.Production) + if (run.scopes.hasTestScope) listOf(CodeScope.Test) else emptyList()
    return codeScopes.flatMap { codeScope ->
        val constructs = run.groups.filter { it.codeScope == codeScope }.flatMap { it.constructs }
        classifiableDeclarations(run.scopes.of(codeScope)).map { declaration ->
            classified(declaration, codeScope, constructs.filter { it.test(declaration) })
        }
    }.sortedWith(compareBy({ it.path }, { it.startLine }))
}

private fun classified(declaration: KoBaseDeclaration, codeScope: CodeScope, constructs: List<Construct<*>>): ClassifiedDeclaration {
    val path = (declaration as KoPathProvider).projectPath.removePrefix("/")
    val startLine = (declaration as KoLocationProvider).location.substringAfterLast(".kt:").substringBefore(':').toInt()
    val lineCount = (declaration as KoTextProvider).text.lines().size
    val name = (declaration as? KoFullyQualifiedNameProvider)?.fullyQualifiedName
        ?: (declaration as? KoNameProvider)?.name
        ?: declaration.toString()
    return ClassifiedDeclaration(path, startLine, startLine + lineCount - 1, name, codeScope, constructs)
}

/**
 * The classification as JSON: the test categories the catalog names, each Construct's test
 * expectation, and [declarations] with the ids of the Constructs each one matches.
 */
fun renderClassificationJson(groups: List<RuleGroup>, declarations: List<ClassifiedDeclaration>): String {
    val constructs = groups.flatMap { it.constructs }
    val categories = constructs
        .flatMap { (it.testedBy as? TestExpectation.AnyOf)?.categories.orEmpty() }
        .distinctBy { it.id }
    return buildString {
        appendLine("{")
        appendLine("  \"categories\": [")
        appendLine(categories.joinToString(",\n") { "    {\"id\": ${it.id.json()}, \"name\": ${it.name.json()}}" })
        appendLine("  ],")
        appendLine("  \"constructs\": [")
        appendLine(
            constructs.joinToString(",\n") { construct ->
                "    {\"id\": ${construct.id.json()}, \"codeScope\": ${construct.owner.codeScope.json()}, \"testedBy\": ${construct.testedBy.json()}}"
            },
        )
        appendLine("  ],")
        appendLine("  \"declarations\": [")
        appendLine(
            declarations.joinToString(",\n") { declaration ->
                "    {\"path\": ${declaration.path.json()}, \"startLine\": ${declaration.startLine}, \"endLine\": ${declaration.endLine}, " +
                    "\"name\": ${declaration.name.json()}, \"codeScope\": ${declaration.codeScope.json()}, " +
                    "\"constructs\": [${declaration.constructs.joinToString { it.id.json() }}]}"
            },
        )
        appendLine("  ]")
        appendLine("}")
    }
}

private fun CodeScope.json(): String = name.lowercase().json()

private fun TestExpectation.json(): String = when (this) {
    TestExpectation.Unspecified -> "{\"kind\": \"unspecified\"}"
    is TestExpectation.None -> "{\"kind\": \"none\", \"reason\": ${reason.json()}}"
    is TestExpectation.AnyOf -> "{\"kind\": \"anyOf\", \"categories\": [${categories.joinToString { it.id.json() }}]}"
}

private fun String.json(): String = buildString {
    append('"')
    this@json.forEach { char ->
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' -> append("\\u%04x".format(char.code))
            else -> append(char)
        }
    }
    append('"')
}
