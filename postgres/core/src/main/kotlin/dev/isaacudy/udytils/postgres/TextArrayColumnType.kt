package dev.isaacudy.udytils.postgres

import org.jetbrains.exposed.v1.core.ColumnType
import java.sql.Array as SqlArray

/**
 * Exposed [ColumnType] for Postgres `TEXT[]`. Exposed core doesn't ship a
 * native array column type, so we wrap the JDBC [SqlArray] path directly.
 * Mutations go through a `List<String>` on the Kotlin side.
 */
class TextArrayColumnType : ColumnType<List<String>>() {
    override fun sqlType(): String = "TEXT[]"

    override fun valueFromDB(value: Any): List<String> = when (value) {
        is SqlArray -> @Suppress("UNCHECKED_CAST") (value.array as Array<String?>)
            .filterNotNull()
            .toList()
        is List<*> -> value.filterIsInstance<String>()
        is Array<*> -> value.filterIsInstance<String>()
        else -> error("Unexpected value for TEXT[]: ${value::class}")
    }

    override fun notNullValueToDB(value: List<String>): Any = value.toTypedArray()

    override fun valueToString(value: List<String>?): String =
        value?.joinToString(",") ?: "null"
}
