package dev.isaacudy.udytils.postgres

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Exposed [ColumnType] for Postgres `TIMESTAMP WITH TIME ZONE`, typed on the
 * Kotlin side as [kotlin.time.Instant].
 *
 * The pgjdbc driver returns TIMESTAMPTZ values as [OffsetDateTime] (or
 * occasionally [java.sql.Timestamp] for legacy code paths); we normalise both
 * to an `Instant`. Writes go out as `OffsetDateTime` at UTC, which pgjdbc binds
 * into the TIMESTAMPTZ column losslessly.
 *
 * This deliberately avoids the exposed-kotlin-datetime module, whose runtime
 * dependency on `kotlinx.datetime.Instant` has been fragile to package on the
 * server classpath. This implementation uses only the Kotlin stdlib's time
 * types, which need no extra dependency to load.
 */
class TimestampColumnType : ColumnType<Instant>() {
    override fun sqlType(): String = "TIMESTAMP WITH TIME ZONE"

    override fun valueFromDB(value: Any): Instant = when (value) {
        is OffsetDateTime -> value.toInstant().toKotlinInstant()
        is java.sql.Timestamp -> value.toInstant().toKotlinInstant()
        is Instant -> value
        else -> error("Unexpected TIMESTAMPTZ value: ${value::class}")
    }

    override fun notNullValueToDB(value: Instant): Any =
        value.toJavaInstant().atOffset(ZoneOffset.UTC)
}

/**
 * Declares a `TIMESTAMP WITH TIME ZONE` column on an Exposed [Table] typed as
 * [kotlin.time.Instant].
 */
fun Table.timestamp(name: String): Column<Instant> =
    registerColumn(name, TimestampColumnType())
