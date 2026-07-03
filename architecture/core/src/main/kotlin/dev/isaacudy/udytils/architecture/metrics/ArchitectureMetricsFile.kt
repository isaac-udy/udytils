package dev.isaacudy.udytils.architecture.metrics

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider
import dev.isaacudy.udytils.architecture.ArchitectureDefinition
import dev.isaacudy.udytils.architecture.ArchitectureRun
import dev.isaacudy.udytils.architecture.NotEnforced
import dev.isaacudy.udytils.architecture.Status
import java.io.File

/**
 * Writes a machine-readable summary of one architecture run — rules and their enforcement tags,
 * a census of declarations per Construct per module, every `@ArchitectureException`, and every
 * audit finding. The udytils metrics plugin adapts this file into metric records; the format is
 * a stable contract, not an implementation detail.
 */
fun writeArchitectureMetrics(definition: ArchitectureDefinition, output: File) {
    val run = ArchitectureRun(definition)
    val scope = definition.scope()
    val repoRoot = findRepoRoot()

    fun relative(path: String): String = path.removePrefix(repoRoot).trimStart('/')
    fun moduleOf(path: String): String = relative(path).substringBefore("/src/")

    val activeRules = run.rules.filter { it.status is Status.Active }

    val declarations = scope.declarations(includeNested = false)
        .filter {
            it is KoClassDeclaration || it is KoInterfaceDeclaration || it is KoObjectDeclaration ||
                it is KoFunctionDeclaration || it is KoPropertyDeclaration
        }
        .filter { definition.membership?.invoke(it) ?: true }
    val census = definition.groups.flatMap { group -> group.constructs }.flatMap { construct ->
        declarations
            .filter { construct.test(it) }
            .groupingBy { decl -> moduleOf((decl as KoPathProvider).path) }
            .eachCount()
            .map { (module, count) -> Triple(construct.id, module, count) }
    }

    val exceptions = buildList {
        scope.files.forEach { file ->
            file.annotations
                .filter { it.name == "ArchitectureException" }
                .forEach { annotation ->
                    ruleIdsOf(annotation.text).forEach { ruleId ->
                        add(ExceptionEntry(ruleId, moduleOf(file.path), relative(file.path)))
                    }
                }
        }
        scope.declarations(includeNested = true)
            .filterIsInstance<KoAnnotationProvider>()
            .forEach { decl ->
                decl.annotations
                    .filter { it.name == "ArchitectureException" }
                    .forEach { annotation ->
                        val path = (decl as KoPathProvider).path
                        ruleIdsOf(annotation.text).forEach { ruleId ->
                            add(ExceptionEntry(ruleId, moduleOf(path), relative(path)))
                        }
                    }
            }
    }.distinct()

    val audits = activeRules
        .filter { (it.enforcement as? NotEnforced)?.audit != null }
        .flatMap { rule ->
            run.auditFindings(rule).map { violation ->
                AuditEntry(rule.id, moduleOf(violation.where), relative(violation.where), violation.message)
            }
        }

    output.parentFile?.mkdirs()
    output.writeText(buildString {
        appendLine("{")
        appendLine("  \"rules\": [")
        appendLine(activeRules.joinToString(",\n") { "    {\"id\": \"${esc(it.id)}\", \"tag\": \"${esc(it.tag.marker)}\"}" })
        appendLine("  ],")
        appendLine("  \"census\": [")
        appendLine(census.joinToString(",\n") { (construct, module, count) ->
            "    {\"construct\": \"${esc(construct)}\", \"module\": \"${esc(module)}\", \"count\": $count}"
        })
        appendLine("  ],")
        appendLine("  \"exceptions\": [")
        appendLine(exceptions.joinToString(",\n") {
            "    {\"ruleId\": \"${esc(it.ruleId)}\", \"module\": \"${esc(it.module)}\", \"where\": \"${esc(it.where)}\"}"
        })
        appendLine("  ],")
        appendLine("  \"audits\": [")
        appendLine(audits.joinToString(",\n") {
            "    {\"ruleId\": \"${esc(it.ruleId)}\", \"module\": \"${esc(it.module)}\", \"where\": \"${esc(it.where)}\", \"message\": \"${esc(it.message)}\"}"
        })
        appendLine("  ]")
        appendLine("}")
    })
}

private data class ExceptionEntry(val ruleId: String, val module: String, val where: String)
private data class AuditEntry(val ruleId: String, val module: String, val where: String, val message: String)

/** The quoted rule IDs inside the annotation's `ruleIds = [...]` argument. */
private fun ruleIdsOf(annotationText: String): List<String> {
    val inside = annotationText.substringAfter("ruleIds", "").substringAfter("[", "").substringBefore("]", "")
    return Regex("\"([A-Za-z0-9_.]+)\"").findAll(inside).map { it.groupValues[1] }.toList()
}

private fun findRepoRoot(): String {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null && !dir.resolve(".git").exists()) dir = dir.parentFile
    return (dir ?: File(System.getProperty("user.dir"))).absolutePath
}

private fun esc(s: String): String = buildString {
    s.forEach { c ->
        when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c == '\n' -> append("\\n")
            c == '\t' -> append("\\t")
            c == '\r' -> {}
            c.code < 0x20 -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
}
