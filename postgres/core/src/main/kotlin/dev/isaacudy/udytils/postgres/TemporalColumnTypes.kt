package dev.isaacudy.udytils.postgres

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Exposed [ColumnType] for Postgres `DATE`, typed as [java.time.LocalDate].
 * Uses only java.time so it needs no extra runtime dependency (unlike
 * exposed-kotlin-datetime / exposed-java-time).
 */
class DateColumnType : ColumnType<LocalDate>() {
    override fun sqlType(): String = "DATE"

    override fun valueFromDB(value: Any): LocalDate = when (value) {
        is LocalDate -> value
        is java.sql.Date -> value.toLocalDate()
        else -> error("Unexpected DATE value: ${value::class}")
    }

    override fun notNullValueToDB(value: LocalDate): Any = value
}

/** Declares a `DATE` column typed as [java.time.LocalDate]. */
fun Table.date(name: String): Column<LocalDate> =
    registerColumn(name, DateColumnType())

/**
 * Exposed [ColumnType] for Postgres `TIMESTAMP` (without time zone), typed as
 * [java.time.LocalDateTime].
 */
class DateTimeColumnType : ColumnType<LocalDateTime>() {
    override fun sqlType(): String = "TIMESTAMP"

    override fun valueFromDB(value: Any): LocalDateTime = when (value) {
        is LocalDateTime -> value
        is java.sql.Timestamp -> value.toLocalDateTime()
        else -> error("Unexpected TIMESTAMP value: ${value::class}")
    }

    override fun notNullValueToDB(value: LocalDateTime): Any = value
}

/** Declares a `TIMESTAMP` (without time zone) column typed as [java.time.LocalDateTime]. */
fun Table.datetime(name: String): Column<LocalDateTime> =
    registerColumn(name, DateTimeColumnType())

/**
 * Exposed [ColumnType] for Postgres `TIME` (without time zone), typed as
 * [java.time.LocalTime].
 */
class TimeColumnType : ColumnType<LocalTime>() {
    override fun sqlType(): String = "TIME"

    override fun valueFromDB(value: Any): LocalTime = when (value) {
        is LocalTime -> value
        is java.sql.Time -> value.toLocalTime()
        else -> error("Unexpected TIME value: ${value::class}")
    }

    override fun notNullValueToDB(value: LocalTime): Any = value
}

/** Declares a `TIME` (without time zone) column typed as [java.time.LocalTime]. */
fun Table.time(name: String): Column<LocalTime> =
    registerColumn(name, TimeColumnType())
