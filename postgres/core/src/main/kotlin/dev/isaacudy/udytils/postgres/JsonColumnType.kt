package dev.isaacudy.udytils.postgres

import org.jetbrains.exposed.v1.core.ColumnType
import org.postgresql.util.PGobject

/**
 * Exposed [ColumnType] for Postgres `JSON` (non-binary). Identical in spirit to
 * [JsonbColumnType] but binds the `json` type rather than `jsonb`. The
 * Kotlin-side value is the raw JSON string.
 */
class JsonColumnType : ColumnType<String>() {
    override fun sqlType(): String = "JSON"

    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value.orEmpty()
        is String -> value
        else -> value.toString()
    }

    override fun notNullValueToDB(value: String): Any =
        PGobject().apply {
            type = "json"
            this.value = value
        }

    override fun valueToString(value: String?): String = value ?: "null"
}
