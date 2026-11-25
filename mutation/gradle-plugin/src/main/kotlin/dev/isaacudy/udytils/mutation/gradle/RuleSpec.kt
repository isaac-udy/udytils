package dev.isaacudy.udytils.mutation.gradle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class RuleSpec(
    val id: String,
    val constructs: List<String> = emptyList(),
    val packages: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val excludedConstructs: List<String> = emptyList(),
    val excludedFiles: List<String> = emptyList(),
    val operators: List<String> = emptyList(),
) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    fun selects(site: Site): Boolean {
        if (operators.isNotEmpty() && site.operator !in operators) return false
        if (constructs.isNotEmpty() && site.constructs.none { c -> constructs.any { matches(it, c) } }) return false
        if (packages.isNotEmpty() && packages.none { matchesPackage(it, site.packageName) }) return false
        if (files.isNotEmpty() && files.none { matchesPath(it, site.path) }) return false
        if (site.constructs.any { c -> excludedConstructs.any { matches(it, c) } }) return false
        if (excludedFiles.any { matchesPath(it, site.path) }) return false
        return true
    }

    companion object {
        fun decode(text: String): RuleSpec = Json.decodeFromString(serializer(), text)

        fun matches(pattern: String, value: String): Boolean =
            if (pattern.endsWith("*")) value.startsWith(pattern.dropLast(1)) else value == pattern

        fun matchesPackage(pattern: String, packageName: String): Boolean {
            val prefix = pattern.removeSuffix(".*").removeSuffix("*")
            return packageName == prefix || packageName.startsWith("$prefix.")
        }

        fun matchesPath(pattern: String, path: String): Boolean = globRegex(pattern).matches(path)

        private fun globRegex(glob: String): Regex {
            val out = StringBuilder()
            var i = 0
            while (i < glob.length) {
                val c = glob[i]
                when {
                    glob.startsWith("**/", i) -> { out.append("(?:.*/)?"); i += 3; continue }
                    glob.startsWith("**", i) -> { out.append(".*"); i += 2; continue }
                    c == '*' -> out.append("[^/]*")
                    c == '?' -> out.append("[^/]")
                    else -> out.append(Regex.escape(c.toString()))
                }
                i++
            }
            return Regex(out.toString())
        }
    }
}
