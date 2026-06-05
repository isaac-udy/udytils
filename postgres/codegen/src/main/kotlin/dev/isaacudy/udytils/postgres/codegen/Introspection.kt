package dev.isaacudy.udytils.postgres.codegen

import java.sql.Connection

/**
 * A parsed Postgres column type: the raw `format_type` string plus the base
 * type name (lower-cased) and any size arguments. Examples:
 *  * `numeric(10,2)`        → base `numeric`, args `[10, 2]`
 *  * `character varying(255)` → base `character varying`, args `[255]`
 *  * `text[]`               → base `text[]`, args `[]`
 *  * `timestamp with time zone` → base as-is, args `[]`
 */
data class SqlTypeInfo(
    val raw: String,
    val base: String,
    val args: List<Int>,
) {
    val precision: Int? get() = args.getOrNull(0)
    val scale: Int? get() = args.getOrNull(1)
    val length: Int? get() = args.getOrNull(0)

    companion object {
        fun parse(raw: String): SqlTypeInfo {
            val trimmed = raw.trim()
            if (trimmed.endsWith("[]")) {
                return SqlTypeInfo(trimmed, trimmed.lowercase(), emptyList())
            }
            val paren = trimmed.indexOf('(')
            if (paren < 0) {
                return SqlTypeInfo(trimmed, trimmed.lowercase(), emptyList())
            }
            val base = trimmed.substring(0, paren).trim().lowercase()
            val close = trimmed.indexOf(')', paren).let { if (it < 0) trimmed.length else it }
            val args = trimmed.substring(paren + 1, close)
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
            return SqlTypeInfo(trimmed, base, args)
        }
    }
}

data class IntrospectedColumn(
    val sqlName: String,
    val typeInfo: SqlTypeInfo,
    val nullable: Boolean,
    val defaultExpr: String?,
)

data class IntrospectedTable(
    val sqlName: String,
    val columns: List<IntrospectedColumn>,
    val primaryKeyColumns: List<String>,
)

/**
 * Pulls a structured view of every user table in the given schema: columns
 * (name + parsed SQL type + nullability + default expression) and the primary
 * key column list.
 */
class TableIntrospector(
    private val conn: Connection,
    private val schemaName: String = CodegenConfig.DEFAULT_SCHEMA,
    private val excludedTables: Set<String> = CodegenConfig.DEFAULT_EXCLUDED_TABLES,
) {
    fun readTables(): List<IntrospectedTable> {
        val names = conn.prepareStatement(
            """
            SELECT c.relname
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = ?
              AND c.relkind = 'r'
            ORDER BY c.relname
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, schemaName)
            stmt.executeQuery().use { rs ->
                val list = mutableListOf<String>()
                while (rs.next()) list += rs.getString(1)
                list
            }
        }
        return names
            .filterNot { it in excludedTables }
            .map { readTable(it) }
    }

    // The schema name is a build-config value, interpolated as a SQL string
    // literal; the table name stays a bind parameter.
    private fun regclass(): String = "('$schemaName' || '.' || ? )::regclass"

    private fun readTable(name: String): IntrospectedTable {
        val columns = conn.prepareStatement(
            """
            SELECT a.attname,
                   format_type(a.atttypid, a.atttypmod) AS typename,
                   NOT a.attnotnull AS nullable,
                   pg_get_expr(ad.adbin, ad.adrelid) AS default_expr
            FROM pg_attribute a
            LEFT JOIN pg_attrdef ad
              ON ad.adrelid = a.attrelid AND ad.adnum = a.attnum
            WHERE a.attrelid = ${regclass()}
              AND a.attnum > 0
              AND NOT a.attisdropped
            ORDER BY a.attnum
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs ->
                val list = mutableListOf<IntrospectedColumn>()
                while (rs.next()) {
                    list += IntrospectedColumn(
                        sqlName = rs.getString("attname"),
                        typeInfo = SqlTypeInfo.parse(rs.getString("typename")),
                        nullable = rs.getBoolean("nullable"),
                        defaultExpr = rs.getString("default_expr"),
                    )
                }
                list
            }
        }

        val primaryKey = conn.prepareStatement(
            """
            SELECT pg_get_constraintdef(c.oid, true) AS definition
            FROM pg_constraint c
            WHERE c.conrelid = ${regclass()}
              AND c.contype = 'p'
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs ->
                if (rs.next()) parsePrimaryKeyColumns(rs.getString("definition")) else emptyList()
            }
        }

        return IntrospectedTable(name, columns, primaryKey)
    }

    /**
     * `pg_get_constraintdef` returns strings like `PRIMARY KEY (col_a, col_b)`.
     * We pull the column names out without trying to parse arbitrary SQL — the
     * format is fixed for primary keys.
     */
    private fun parsePrimaryKeyColumns(definition: String): List<String> {
        val open = definition.indexOf('(')
        val close = definition.lastIndexOf(')')
        require(open >= 0 && close > open) {
            "Unexpected PRIMARY KEY definition: $definition"
        }
        return definition.substring(open + 1, close)
            .split(',')
            .map { it.trim().trim('"') }
    }
}
