package dev.isaacudy.udytils.postgres.codegen

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end test: boots a real embedded Postgres, applies a fixture migration,
 * introspects, and asserts both the generated Exposed source and the schema
 * snapshot. Independent of any consuming project, so the engine has CI coverage
 * on its own.
 */
class CodegenGoldenTest {

    private val fixture = """
        CREATE TABLE widgets (
            id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
            name text NOT NULL,
            qty integer NOT NULL,
            price numeric(10,2),
            tags text[] NOT NULL,
            meta jsonb,
            created_at timestamp with time zone NOT NULL DEFAULT now()
        );
        CREATE TABLE categories (
            id integer PRIMARY KEY,
            label varchar(120) NOT NULL
        );
    """.trimIndent()

    private fun migrationsDir(): java.io.File {
        val dir = Files.createTempDirectory("codegen-fixture").toFile()
        java.io.File(dir, "V1__init.sql").writeText(fixture)
        return dir
    }

    @Test
    fun generatesExpectedTableSource() {
        val rendered = withMigratedDatabase(migrationsDir(), "test") { conn ->
            val tables = TableIntrospector(conn).readTables()
            val widgets = tables.single { it.sqlName == "widgets" }
            TableSourceWriter(
                table = widgets,
                nameMapper = NameMapper(),
                typeRegistry = TypeRegistry("dev.isaacudy.udytils.postgres"),
                outputPackage = "com.example.tables",
            ).render()
        }

        assertTrue("package com.example.tables" in rendered, rendered)
        assertTrue("object WidgetsTable : Table(\"widgets\") {" in rendered, rendered)
        assertTrue("data class WidgetRow(" in rendered, rendered)
        assertTrue("val id: Column<Uuid> = uuid(\"id\").autoGenerate()" in rendered, rendered)
        assertTrue("val name: Column<String> = text(\"name\")" in rendered, rendered)
        assertTrue("val qty: Column<Int> = integer(\"qty\")" in rendered, rendered)
        assertTrue("val price: Column<BigDecimal?> = decimal(\"price\", 10, 2).nullable()" in rendered, rendered)
        assertTrue("val tags: Column<List<String>> = registerColumn(\"tags\", TextArrayColumnType())" in rendered, rendered)
        assertTrue("val meta: Column<String?> = registerColumn(\"meta\", JsonbColumnType()).nullable()" in rendered, rendered)
        assertTrue("val createdAt: Column<Instant> = timestamp(\"created_at\")" in rendered, rendered)
        assertTrue("override val primaryKey = PrimaryKey(id)" in rendered, rendered)
        assertTrue("fun WidgetRow(row: ResultRow): WidgetRow = WidgetRow(" in rendered, rendered)
        assertTrue("fun UpdateBuilder<*>.setFromRow(row: WidgetRow) {" in rendered, rendered)
        assertTrue("import dev.isaacudy.udytils.postgres.timestamp" in rendered, rendered)
        assertTrue("import dev.isaacudy.udytils.postgres.JsonbColumnType" in rendered, rendered)
    }

    @Test
    fun exportsNormalisedSchemaSnapshot() {
        val snapshot = withMigratedDatabase(migrationsDir(), "test") { conn ->
            SchemaExporter(conn).export()
        }
        assertTrue("CREATE TABLE widgets (" in snapshot, snapshot)
        assertTrue("CREATE TABLE categories (" in snapshot, snapshot)
        // flyway bookkeeping table is excluded by default
        assertTrue("flyway_schema_history" !in snapshot, snapshot)
    }

    @Test
    fun emptyMigrationsProduceNoTables() {
        val tables = withMigratedDatabase(emptyMigrationsDir(), "test") { conn ->
            TableIntrospector(conn).readTables()
        }
        assertEquals(emptyList(), tables)
    }

    private fun emptyMigrationsDir(): java.io.File {
        val dir = Files.createTempDirectory("codegen-empty").toFile()
        // Flyway needs at least one migration to run; an empty schema results.
        java.io.File(dir, "V1__noop.sql").writeText("SELECT 1;")
        return dir
    }
}
