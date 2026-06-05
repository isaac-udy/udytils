package dev.isaacudy.udytils.postgres.codegen

/**
 * Renders one Kotlin source file for an [IntrospectedTable]:
 *
 *   * an Exposed `Table` object (`XxxTable`, plural) whose columns mirror the
 *     SQL columns 1:1 with primitive Kotlin types,
 *   * a top-level `XxxRow` data class (singular) carrying those columns,
 *   * a "fake constructor" `fun XxxRow(row: ResultRow): XxxRow` so call sites
 *     read like `XxxRow(resultRow)`,
 *   * an extension `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` that copies
 *     every column into an insert/upsert/update statement.
 *
 * Domain-typed wrappers (value classes, enums, paths) and the inverse
 * `Row → Domain` mappers are deliberately left to hand-written code.
 */
class TableSourceWriter(
    private val table: IntrospectedTable,
    private val nameMapper: NameMapper,
    private val typeRegistry: TypeRegistry,
    private val outputPackage: String,
    private val banner: String = CodegenConfig.DEFAULT_BANNER,
) {
    private val tableTypeName: String = nameMapper.tableTypeName(table.sqlName)
    private val rowTypeName: String = nameMapper.rowTypeName(table.sqlName)

    /** The Kotlin file name (and type name) this table renders to. */
    val fileName: String get() = tableTypeName

    fun render(): String {
        val mapped = table.columns.map { typeRegistry.map(table.sqlName, it) }
        val imports = buildImports(mapped)

        return buildString {
            appendLine("// ==========================================================")
            appendLine("// $banner")
            appendLine("// ==========================================================")
            appendLine("package $outputPackage")
            appendLine()
            for (import in imports) appendLine("import $import")
            appendLine()
            appendLine("object $tableTypeName : Table(\"${table.sqlName}\") {")
            for (column in mapped) {
                appendLine("    val ${column.kotlinName}: Column<${column.kotlinType}> = ${column.factoryExpression}")
            }
            if (table.primaryKeyColumns.isNotEmpty()) {
                val pkCols = table.primaryKeyColumns.joinToString(", ") { it.snakeToCamel() }
                appendLine()
                appendLine("    override val primaryKey = PrimaryKey($pkCols)")
            }
            appendLine("}")
            appendLine()
            appendLine("data class $rowTypeName(")
            for (column in mapped) {
                appendLine("    val ${column.kotlinName}: ${column.kotlinType},")
            }
            appendLine(")")
            appendLine()
            appendLine("/** Build a [$rowTypeName] from a Postgres result row. */")
            appendLine("@Suppress(\"FunctionName\")")
            appendLine("fun $rowTypeName(row: ResultRow): $rowTypeName = $rowTypeName(")
            for (column in mapped) {
                appendLine("    ${column.kotlinName} = row[$tableTypeName.${column.kotlinName}],")
            }
            appendLine(")")
            appendLine()
            appendLine("/** Copy every column of [row] into this insert/upsert/update statement. */")
            appendLine("fun UpdateBuilder<*>.setFromRow(row: $rowTypeName) {")
            for (column in mapped) {
                appendLine("    this[$tableTypeName.${column.kotlinName}] = row.${column.kotlinName}")
            }
            append("}")
            appendLine()
        }
    }

    private fun buildImports(columns: List<MappedColumn>): List<String> {
        val set = sortedSetOf<String>()
        set += "org.jetbrains.exposed.sql.Column"
        set += "org.jetbrains.exposed.sql.ResultRow"
        set += "org.jetbrains.exposed.sql.Table"
        set += "org.jetbrains.exposed.sql.statements.UpdateBuilder"
        for (column in columns) set += column.imports
        return set.toList()
    }
}
