package dev.isaacudy.udytils.postgres.codegen

/**
 * snake_case → Kotlin name conversions and table/row name derivation.
 *
 * The singularizer is intentionally conservative: it must reproduce the
 * obvious cases (`campaigns`→`campaign`, `entities`→`entity`,
 * `chat_messages`→`chat_message`) AND avoid the classic mangles a naive
 * "drop trailing s" rule produces (`statuses`→`statuse`, `addresses`→
 * `addresse`, `series`→`sery`). Anything it still can't handle is covered by
 * the per-table `rowNameOverrides` escape hatch.
 */
class NameMapper(
    private val tableSuffix: String = "Table",
    private val rowSuffix: String = "Row",
    /** sqlTableName → singular PascalCase base (e.g. "people" → "Person"). */
    private val rowNameOverrides: Map<String, String> = emptyMap(),
) {
    fun tableTypeName(sqlTable: String): String = sqlTable.snakeToPascal() + tableSuffix

    fun rowTypeName(sqlTable: String): String {
        rowNameOverrides[sqlTable]?.let { return it + rowSuffix }
        return sqlTable.snakeToSingularPascal() + rowSuffix
    }

    fun columnName(sqlColumn: String): String = sqlColumn.snakeToCamel()
}

internal fun String.snakeToCamel(): String {
    val parts = split('_').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return this
    return parts.first().lowercase() +
        parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
}

internal fun String.snakeToPascal(): String {
    val parts = split('_').filter { it.isNotEmpty() }
    return parts.joinToString("") { it.replaceFirstChar(Char::uppercase) }
}

internal fun String.snakeToSingularPascal(): String {
    val parts = split('_').filter { it.isNotEmpty() }.toMutableList()
    if (parts.isNotEmpty()) {
        parts[parts.lastIndex] = singularize(parts.last())
    }
    return parts.joinToString("") { it.replaceFirstChar(Char::uppercase) }
}

/** Irregular plurals worth covering out of the box. */
private val IRREGULARS: Map<String, String> = mapOf(
    "people" to "person",
    "children" to "child",
    "men" to "man",
    "women" to "woman",
    "feet" to "foot",
    "teeth" to "tooth",
    "geese" to "goose",
    "mice" to "mouse",
    "indices" to "index",
    "matrices" to "matrix",
    "vertices" to "vertex",
    "analyses" to "analysis",
    "diagnoses" to "diagnosis",
    "theses" to "thesis",
    "crises" to "crisis",
)

/**
 * Words that are the same singular and plural, or are already singular but end
 * in `s` — must NOT have a trailing `s` stripped.
 */
private val UNINFLECTED: Set<String> = setOf(
    "series", "species", "news", "data", "media", "metadata",
    "status", "bonus", "campus", "alias", "address", // (address handled by -es too, kept for safety)
    "info", "settings", "metrics",
)

internal fun singularize(wordRaw: String): String {
    val lower = wordRaw.lowercase()
    IRREGULARS[lower]?.let { return it }
    if (lower in UNINFLECTED) return wordRaw

    return when {
        // "categories" → "category"; not "series" (handled above)
        lower.endsWith("ies") && wordRaw.length > 3 -> wordRaw.dropLast(3) + "y"
        // "knives" → "knife", "leaves" → "leaf"
        lower.endsWith("ves") && wordRaw.length > 3 -> wordRaw.dropLast(3) + "f"
        // "statuses" → "status", "boxes" → "box", "matches" → "match",
        // "wishes" → "wish", "buzzes" → "buzz". NOTE: must come BEFORE the
        // bare-"s" rule and must NOT fire for "messages"/"images" (which end
        // in "ges", not one of these clusters).
        lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes") ||
            lower.endsWith("ches") || lower.endsWith("shes") -> wordRaw.dropLast(2)
        // double-s singular (e.g. "address") — leave alone
        lower.endsWith("ss") -> wordRaw
        // regular plural: "campaigns" → "campaign", "messages" → "message"
        lower.endsWith("s") && wordRaw.length > 1 -> wordRaw.dropLast(1)
        else -> wordRaw
    }
}
