package dev.isaacudy.udytils.postgres

import org.jetbrains.exposed.sql.ColumnType
import org.postgresql.util.PGobject

/**
 * Exposed [ColumnType] for Postgres `JSONB`. The Kotlin-side value is the raw
 * JSON string — domain mappers handle (de)serialization above this layer via
 * kotlinx.serialization. Storing strings keeps this column type small and
 * codec-agnostic; richer parsing belongs in the mapper.
 *
 * Writes wrap the string in a [PGobject] with `type = "jsonb"` so the driver
 * issues the correct binding (without this, Postgres rejects the insert with
 * `column "x" is of type jsonb but expression is of type character varying`).
 * Reads accept either `PGobject`, `String`, or any value with a sensible
 * `toString()` so this works against the live DB, tests, and wrapper drivers
 * that pre-unwrap.
 */
class JsonbColumnType : ColumnType<String>() {
    override fun sqlType(): String = "JSONB"

    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value.orEmpty()
        is String -> value
        else -> value.toString()
    }

    override fun notNullValueToDB(value: String): Any =
        PGobject().apply {
            type = "jsonb"
            this.value = value
        }

    override fun valueToString(value: String?): String = value ?: "null"
}
