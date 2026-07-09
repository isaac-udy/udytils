package dev.isaacudy.udytils.postgres.codegen

/** One column mapped to its Kotlin type, Exposed factory call, and imports. */
data class MappedColumn(
    val kotlinName: String,
    /** Kotlin type, including a trailing `?` when nullable. */
    val kotlinType: String,
    /** Exposed column factory expression, including `.nullable()` when nullable. */
    val factoryExpression: String,
    val imports: Set<String>,
)

/**
 * Maps parsed Postgres column types to Exposed column factory calls. The
 * built-in registry covers the standard scalar set; [overrides] (from the
 * `postgresCodegen { sqlTypeOverrides }` DSL) take precedence and can also
 * add support for exotic types. Unknown types fail fast with a message that
 * names the type and points at the override hook.
 */
class TypeRegistry(
    private val columnTypesPackage: String,
    private val overrides: Map<String, TypeOverride> = emptyMap(),
) {
    fun map(table: String, column: IntrospectedColumn): MappedColumn {
        val info = column.typeInfo
        val sqlName = column.sqlName
        val kotlinName = sqlName.snakeToCamel()

        overrides[info.base]?.let { ov ->
            val factory = applyTemplate(ov.factoryTemplate, sqlName, info)
            return finalize(kotlinName, ov.kotlinType, factory, column.nullable, ov.imports)
        }

        fun ctp(symbol: String) = "$columnTypesPackage.$symbol"

        return when (info.base) {
            "text" -> built(kotlinName, "String", "text(\"$sqlName\")", column.nullable)
            "integer", "int", "int4", "serial" ->
                built(kotlinName, "Int", "integer(\"$sqlName\")", column.nullable)
            "bigint", "int8", "bigserial" ->
                built(kotlinName, "Long", "long(\"$sqlName\")", column.nullable)
            "smallint", "int2", "smallserial" ->
                built(kotlinName, "Short", "short(\"$sqlName\")", column.nullable)
            "boolean", "bool" ->
                built(kotlinName, "Boolean", "bool(\"$sqlName\")", column.nullable)
            "real", "float4" ->
                built(kotlinName, "Float", "float(\"$sqlName\")", column.nullable)
            "double precision", "float8" ->
                built(kotlinName, "Double", "double(\"$sqlName\")", column.nullable)
            "numeric", "decimal" -> {
                val precision = info.precision ?: 38
                val scale = info.scale ?: 18
                built(
                    kotlinName, "BigDecimal",
                    "decimal(\"$sqlName\", $precision, $scale)",
                    column.nullable, setOf("java.math.BigDecimal"),
                )
            }
            "uuid" -> {
                // Exposed 1.x's Table.uuid() is native kotlin.uuid.Uuid
                // (Column<Uuid>). Emit .autoGenerate() for UUID columns whose DB
                // default generates one, so Exposed batchInsert doesn't reject
                // the table (DB-side defaults aren't honoured by batch inserts).
                // Cover both gen_random_uuid() (pgcrypto/core) and
                // uuid_generate_v4() (uuid-ossp). On Column<Uuid> the no-arg
                // .autoGenerate() resolves to Exposed's kotlin.uuid extension
                // (@JvmName("autoGenerateKotlinUuid")) — a clientDefault of
                // Uuid.random(), the Column<Uuid> equivalent of the old java
                // client-side default.
                val auto = column.defaultExpr?.let {
                    it.contains("gen_random_uuid") || it.contains("uuid_generate_v4")
                } == true
                val factory = if (auto) "uuid(\"$sqlName\").autoGenerate()" else "uuid(\"$sqlName\")"
                built(kotlinName, "Uuid", factory, column.nullable, setOf("kotlin.uuid.Uuid"))
            }
            "timestamp with time zone", "timestamptz" -> built(
                kotlinName, "Instant", "timestamp(\"$sqlName\")", column.nullable,
                setOf("kotlin.time.Instant", ctp("timestamp")),
            )
            "timestamp", "timestamp without time zone" -> built(
                kotlinName, "LocalDateTime", "datetime(\"$sqlName\")", column.nullable,
                setOf("java.time.LocalDateTime", ctp("datetime")),
            )
            "date" -> built(
                kotlinName, "LocalDate", "date(\"$sqlName\")", column.nullable,
                setOf("java.time.LocalDate", ctp("date")),
            )
            "time", "time without time zone" -> built(
                kotlinName, "LocalTime", "time(\"$sqlName\")", column.nullable,
                setOf("java.time.LocalTime", ctp("time")),
            )
            "character varying", "varchar" -> {
                val len = info.length
                if (len != null) {
                    built(kotlinName, "String", "varchar(\"$sqlName\", $len)", column.nullable)
                } else {
                    // Unbounded varchar — Exposed varchar needs a length, so map
                    // to text (same Kotlin String, no length constraint).
                    built(kotlinName, "String", "text(\"$sqlName\")", column.nullable)
                }
            }
            "character", "char", "bpchar" -> {
                val len = info.length ?: 1
                built(kotlinName, "String", "char(\"$sqlName\", $len)", column.nullable)
            }
            "bytea" -> built(kotlinName, "ByteArray", "binary(\"$sqlName\")", column.nullable)
            "jsonb" -> built(
                kotlinName, "String", "registerColumn(\"$sqlName\", JsonbColumnType())",
                column.nullable, setOf(ctp("JsonbColumnType")),
            )
            "json" -> built(
                kotlinName, "String", "registerColumn(\"$sqlName\", JsonColumnType())",
                column.nullable, setOf(ctp("JsonColumnType")),
            )
            "text[]" -> built(
                kotlinName, "List<String>", "registerColumn(\"$sqlName\", TextArrayColumnType())",
                column.nullable, setOf(ctp("TextArrayColumnType")),
            )
            else -> error(
                "Unsupported SQL type '${info.raw}' on $table.$sqlName. Add a mapping via " +
                    "postgresCodegen { sqlTypeOverrides.put(\"${info.base}\", ...) }, or use a " +
                    "supported type.",
            )
        }
    }

    private fun built(
        kotlinName: String,
        kotlinType: String,
        factory: String,
        nullable: Boolean,
        extraImports: Set<String> = emptySet(),
    ): MappedColumn = finalize(kotlinName, kotlinType, factory, nullable, extraImports)

    private fun finalize(
        kotlinName: String,
        kotlinType: String,
        factory: String,
        nullable: Boolean,
        extraImports: Set<String>,
    ): MappedColumn {
        val finalType = if (nullable) "$kotlinType?" else kotlinType
        val finalFactory = if (nullable) "$factory.nullable()" else factory
        return MappedColumn(kotlinName, finalType, finalFactory, extraImports)
    }

    private fun applyTemplate(template: String, sqlName: String, info: SqlTypeInfo): String =
        template
            .replace("{name}", sqlName)
            .replace("{precision}", (info.precision ?: 38).toString())
            .replace("{scale}", (info.scale ?: 0).toString())
            .replace("{length}", (info.length ?: 0).toString())
}
