package dev.isaacudy.udytils.mutation.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/** One mutant site from a compiler-plugin manifest, joined with its architecture data. */
internal data class Site(
    val id: Long,
    val path: String,
    val module: String?,
    val packageName: String,
    val line: Int,
    val operator: String,
    val declaration: String,
    val text: String,
    val initScope: Boolean,
    val constructs: List<String> = emptyList(),
    val expectation: Expectation = Expectation.Unclassified,
) {
    val hexId: String get() = java.lang.Long.toHexString(id)
}

internal sealed interface Expectation {
    data class AnyOf(val categories: Set<String>) : Expectation
    data class None(val reason: String) : Expectation

    /** No Construct and no module expectation: a test in any category counts. */
    data object Unclassified : Expectation

    /** Removes [excluded] categories; an expectation left with no category becomes [None]. */
    fun without(excluded: Set<String>): Expectation {
        if (this !is AnyOf || categories.none { it in excluded }) return this
        val left = categories - excluded
        return if (left.isEmpty()) None("expects only ${categories.sorted().joinToString()}, which mutation runs exclude") else AnyOf(left)
    }

    fun accepts(tags: Set<String>): Boolean = when (this) {
        is AnyOf -> tags.any { it in categories }
        is None -> false
        Unclassified -> true
    }

    companion object {
        fun decode(encoded: String): Expectation = when {
            encoded.startsWith("anyOf:") -> AnyOf(encoded.removePrefix("anyOf:").split(',').filter(String::isNotBlank).toSet())
            encoded.startsWith("none:") -> None(encoded.removePrefix("none:"))
            else -> error("Module expectation \"$encoded\" must start with anyOf: or none:.")
        }
    }
}

internal object Manifests {
    private val json = Json { ignoreUnknownKeys = true }

    /** Reads every manifest under [directories]; drops manifests whose source file no longer exists. */
    fun read(directories: Collection<File>, repository: File, modules: Map<String, String>): List<Site> {
        val byId = LinkedHashMap<Long, Site>()
        val moduleDirs = modules.entries.sortedByDescending { it.value.length }
        for (dir in directories) {
            dir.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedBy { it.path }.forEach { file ->
                val root = json.parseToJsonElement(file.readText()).jsonObject
                val source = File(root.string("file"))
                if (!source.isFile) return@forEach
                val path = source.relativeToOrSelf(repository).invariantSeparatorsPath
                val module = moduleDirs.firstOrNull { path.startsWith(it.value + "/") }?.key
                for (entry in root.getValue("mutants").jsonArray) {
                    val m = entry.jsonObject
                    val site = Site(
                        id = m.getValue("id").jsonPrimitive.long,
                        path = path,
                        module = module,
                        packageName = root.string("package"),
                        line = m.getValue("line").jsonPrimitive.content.toInt(),
                        operator = m.string("operator"),
                        declaration = m.string("declaration"),
                        text = m.string("text"),
                        initScope = m.getValue("initScope").jsonPrimitive.boolean,
                    )
                    byId.putIfAbsent(site.id, site)
                }
            }
        }
        return byId.values.toList()
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
}

/** The declarations and Constructs from the architecture `classification.json`. */
internal class Classification(
    private val declarationsByPath: Map<String, List<Declaration>>,
    private val expectations: Map<String, Expectation>,
) {
    class Declaration(val startLine: Int, val endLine: Int, val constructs: List<String>)

    fun constructsAt(path: String, line: Int): List<String> =
        declarationsByPath[path].orEmpty()
            .filter { line in it.startLine..it.endLine }
            .minByOrNull { it.endLine - it.startLine }
            ?.constructs.orEmpty()

    /** The union of the Constructs' expected categories; [Expectation.None] only when every Construct expects none. */
    fun expectationOf(constructs: List<String>): Expectation? {
        val known = constructs.mapNotNull { expectations[it] }
        if (known.isEmpty()) return null
        val categories = known.filterIsInstance<Expectation.AnyOf>().flatMap { it.categories }.toSet()
        return if (categories.isNotEmpty()) Expectation.AnyOf(categories) else known.first()
    }

    companion object {
        val Empty = Classification(emptyMap(), emptyMap())

        fun read(file: File): Classification {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val expectations = root.getValue("constructs").jsonArray.associate { element ->
                val construct = element.jsonObject
                val testedBy = construct.getValue("testedBy").jsonObject
                val expectation = when (testedBy.getValue("kind").jsonPrimitive.content) {
                    "anyOf" -> Expectation.AnyOf(testedBy.getValue("categories").jsonArray.map { it.jsonPrimitive.content }.toSet())
                    "none" -> Expectation.None(testedBy["reason"]?.jsonPrimitive?.content.orEmpty())
                    else -> Expectation.Unclassified
                }
                construct.getValue("id").jsonPrimitive.content to expectation
            }
            val declarations = root.getValue("declarations").jsonArray.map { it.jsonObject }.groupBy(
                keySelector = { it.getValue("path").jsonPrimitive.content },
                valueTransform = { d ->
                    Declaration(
                        startLine = d.getValue("startLine").jsonPrimitive.content.toInt(),
                        endLine = d.getValue("endLine").jsonPrimitive.content.toInt(),
                        constructs = d.getValue("constructs").jsonArray.map { it.jsonPrimitive.content },
                    )
                },
            )
            return Classification(declarations, expectations)
        }
    }
}
