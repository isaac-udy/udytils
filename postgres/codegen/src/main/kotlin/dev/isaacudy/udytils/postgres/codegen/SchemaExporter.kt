package dev.isaacudy.udytils.postgres.codegen

import java.sql.Connection

/**
 * Produces a normalised SQL snapshot of a running Postgres database's user
 * schema. Emits (in order):
 *
 *  1. `CREATE FUNCTION` statements for every user-defined function, pulled via
 *     `pg_get_functiondef` so plpgsql bodies come back verbatim.
 *  2. `CREATE TABLE` statements including columns, constraints, and per-column
 *     defaults. Constraint names and ordering are stable.
 *  3. `CREATE INDEX` statements for any index that isn't already implied by a
 *     constraint on its table.
 *  4. `CREATE TRIGGER` statements.
 *
 * All lookups restrict to [schemaName]. Tables in [excludedTables] (e.g. the
 * migration tool's bookkeeping table) are omitted.
 */
class SchemaExporter(
    private val conn: Connection,
    private val schemaName: String = CodegenConfig.DEFAULT_SCHEMA,
    private val excludedTables: Set<String> = CodegenConfig.DEFAULT_EXCLUDED_TABLES,
    private val banner: String = CodegenConfig.DEFAULT_BANNER,
) {
    fun export(): String = buildString {
        appendLine("-- ==========================================================")
        appendLine("-- $banner")
        appendLine("-- ==========================================================")
        appendLine()

        val functions = readFunctions()
        if (functions.isNotEmpty()) {
            appendLine("-- ----- Functions -----")
            for (fn in functions) {
                appendLine(fn.definition.trim())
                appendLine()
            }
        }

        val tables = readTables()
        if (tables.isNotEmpty()) {
            appendLine("-- ----- Tables -----")
            for (table in tables) {
                appendLine(renderCreateTable(table))
                appendLine()
            }
        }

        val indexes = readNonConstraintIndexes(tables.map { it.name }.toSet())
        if (indexes.isNotEmpty()) {
            appendLine("-- ----- Indexes -----")
            for (index in indexes) {
                appendLine("${index.definition.trim()};")
            }
            appendLine()
        }

        val triggers = readTriggers()
        if (triggers.isNotEmpty()) {
            appendLine("-- ----- Triggers -----")
            for (trigger in triggers) {
                appendLine("${trigger.definition.trim()};")
            }
            appendLine()
        }
    }

    // === Functions ===========================================================

    private data class FunctionDef(val name: String, val definition: String)

    private fun readFunctions(): List<FunctionDef> {
        val sql = """
            SELECT p.proname, pg_get_functiondef(p.oid) AS definition
            FROM pg_proc p
            JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = ?
              AND p.prokind = 'f'
            ORDER BY p.proname
        """.trimIndent()
        return conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schemaName)
            stmt.executeQuery().use { rs ->
                val out = mutableListOf<FunctionDef>()
                while (rs.next()) {
                    out += FunctionDef(rs.getString("proname"), rs.getString("definition"))
                }
                out
            }
        }
    }

    // === Tables ==============================================================

    private data class Column(val name: String, val type: String, val nullable: Boolean, val default: String?)
    private data class Constraint(val name: String, val type: String, val definition: String)
    private data class TableDef(val name: String, val columns: List<Column>, val constraints: List<Constraint>)

    private fun regclass(): String = "('$schemaName' || '.' || ? )::regclass"

    private fun readTables(): List<TableDef> {
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
        return names.filterNot { it in excludedTables }.map { readTableDef(it) }
    }

    private fun readTableDef(name: String): TableDef {
        val columns = conn.prepareStatement(
            """
            SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS typename,
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
                val list = mutableListOf<Column>()
                while (rs.next()) {
                    list += Column(
                        name = rs.getString("attname"),
                        type = rs.getString("typename"),
                        nullable = rs.getBoolean("nullable"),
                        default = rs.getString("default_expr"),
                    )
                }
                list
            }
        }

        val constraints = conn.prepareStatement(
            """
            SELECT c.conname, c.contype, pg_get_constraintdef(c.oid, true) AS definition
            FROM pg_constraint c
            WHERE c.conrelid = ${regclass()}
            ORDER BY c.contype, c.conname
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs ->
                val list = mutableListOf<Constraint>()
                while (rs.next()) {
                    list += Constraint(
                        name = rs.getString("conname"),
                        type = rs.getString("contype"),
                        definition = rs.getString("definition"),
                    )
                }
                list
            }
        }

        return TableDef(name, columns, constraints)
    }

    private fun renderCreateTable(table: TableDef): String = buildString {
        appendLine("CREATE TABLE ${table.name} (")
        val lines = mutableListOf<String>()
        for (column in table.columns) {
            val pieces = mutableListOf(column.name, column.type)
            if (!column.nullable) pieces += "NOT NULL"
            column.default?.let { pieces += "DEFAULT $it" }
            lines += "    ${pieces.joinToString(" ")}"
        }
        for (constraint in table.constraints) {
            lines += "    CONSTRAINT ${constraint.name} ${constraint.definition}"
        }
        append(lines.joinToString(",\n"))
        appendLine()
        append(");")
    }

    // === Indexes =============================================================

    private data class IndexDef(val name: String, val table: String, val definition: String)

    private fun readNonConstraintIndexes(tables: Set<String>): List<IndexDef> {
        val sql = """
            SELECT i.indexname, i.tablename, i.indexdef
            FROM pg_indexes i
            LEFT JOIN pg_constraint c
              ON c.conname = i.indexname
              AND c.connamespace = '$schemaName'::regnamespace
            WHERE i.schemaname = ?
              AND c.oid IS NULL
            ORDER BY i.tablename, i.indexname
        """.trimIndent()
        return conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schemaName)
            stmt.executeQuery().use { rs ->
                val list = mutableListOf<IndexDef>()
                while (rs.next()) {
                    val table = rs.getString("tablename")
                    if (table in tables) {
                        list += IndexDef(rs.getString("indexname"), table, rs.getString("indexdef"))
                    }
                }
                list
            }
        }
    }

    // === Triggers ============================================================

    private data class TriggerDef(val name: String, val table: String, val definition: String)

    private fun readTriggers(): List<TriggerDef> {
        val sql = """
            SELECT t.tgname, c.relname AS table_name, pg_get_triggerdef(t.oid, true) AS definition
            FROM pg_trigger t
            JOIN pg_class c ON c.oid = t.tgrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = ?
              AND NOT t.tgisinternal
            ORDER BY c.relname, t.tgname
        """.trimIndent()
        return conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schemaName)
            stmt.executeQuery().use { rs ->
                val list = mutableListOf<TriggerDef>()
                while (rs.next()) {
                    val table = rs.getString("table_name")
                    if (table in excludedTables) continue
                    list += TriggerDef(rs.getString("tgname"), table, rs.getString("definition"))
                }
                list
            }
        }
    }
}
