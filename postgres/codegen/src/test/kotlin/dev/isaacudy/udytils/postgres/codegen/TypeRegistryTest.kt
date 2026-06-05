package dev.isaacudy.udytils.postgres.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TypeRegistryTest {
    private val ctp = "dev.isaacudy.udytils.postgres"
    private val reg = TypeRegistry(ctp)

    private fun col(name: String, type: String, nullable: Boolean = false, def: String? = null) =
        IntrospectedColumn(name, SqlTypeInfo.parse(type), nullable, def)

    @Test
    fun scalarsReproduceOriginalFactories() {
        assertEquals("text(\"name\")", reg.map("t", col("name", "text")).factoryExpression)
        assertEquals("integer(\"n\")", reg.map("t", col("n", "integer")).factoryExpression)
        assertEquals("long(\"n\")", reg.map("t", col("n", "bigint")).factoryExpression)
        assertEquals("bool(\"b\")", reg.map("t", col("b", "boolean")).factoryExpression)
        assertEquals("double(\"d\")", reg.map("t", col("d", "double precision")).factoryExpression)
        assertEquals("char(\"c\", 36)", reg.map("t", col("c", "character(36)")).factoryExpression)
    }

    @Test
    fun nullableAppendsNullable() {
        val c = reg.map("t", col("x", "integer", nullable = true))
        assertEquals("Int?", c.kotlinType)
        assertEquals("integer(\"x\").nullable()", c.factoryExpression)
    }

    @Test
    fun uuidAutoGenerateForBothFunctions() {
        assertEquals("uuid(\"id\").autoGenerate()", reg.map("t", col("id", "uuid", def = "gen_random_uuid()")).factoryExpression)
        assertEquals("uuid(\"id\").autoGenerate()", reg.map("t", col("id", "uuid", def = "uuid_generate_v4()")).factoryExpression)
        assertEquals("uuid(\"id\")", reg.map("t", col("id", "uuid")).factoryExpression)
    }

    @Test
    fun newScalarTypesForGenerality() {
        assertEquals("short(\"s\")", reg.map("t", col("s", "smallint")).factoryExpression)
        assertEquals("float(\"r\")", reg.map("t", col("r", "real")).factoryExpression)
        assertEquals("binary(\"b\")", reg.map("t", col("b", "bytea")).factoryExpression)
        assertEquals("varchar(\"v\", 255)", reg.map("t", col("v", "character varying(255)")).factoryExpression)
        assertEquals("text(\"v\")", reg.map("t", col("v", "character varying")).factoryExpression)
    }

    @Test
    fun numericParsesPrecisionScale() {
        val c = reg.map("t", col("amount", "numeric(10,2)"))
        assertEquals("BigDecimal", c.kotlinType)
        assertEquals("decimal(\"amount\", 10, 2)", c.factoryExpression)
        assertTrue("java.math.BigDecimal" in c.imports)
    }

    @Test
    fun temporalTypesImportColumnTypesPackage() {
        val tz = reg.map("t", col("created_at", "timestamp with time zone"))
        assertEquals("timestamp(\"created_at\")", tz.factoryExpression)
        assertTrue("kotlin.time.Instant" in tz.imports)
        assertTrue("$ctp.timestamp" in tz.imports)

        val dt = reg.map("t", col("at", "timestamp without time zone"))
        assertEquals("datetime(\"at\")", dt.factoryExpression)
        assertTrue("$ctp.datetime" in dt.imports)

        val d = reg.map("t", col("d", "date"))
        assertEquals("date(\"d\")", d.factoryExpression)
        assertTrue("$ctp.date" in d.imports)
    }

    @Test
    fun jsonAndArrayUseRegisterColumn() {
        assertEquals("registerColumn(\"data\", JsonbColumnType())", reg.map("t", col("data", "jsonb")).factoryExpression)
        assertEquals("registerColumn(\"data\", JsonColumnType())", reg.map("t", col("data", "json")).factoryExpression)
        assertEquals("registerColumn(\"tags\", TextArrayColumnType())", reg.map("t", col("tags", "text[]")).factoryExpression)
    }

    @Test
    fun overridesTakePrecedenceAndApplyTemplate() {
        val r = TypeRegistry(ctp, mapOf("citext" to TypeOverride("String", "text(\"{name}\")", emptySet())))
        assertEquals("text(\"email\")", r.map("t", col("email", "citext")).factoryExpression)
    }

    @Test
    fun unknownTypeFailsFastWithHelpfulMessage() {
        val ex = assertFailsWith<IllegalStateException> { reg.map("t", col("addr", "cidr")) }
        assertTrue("cidr" in ex.message!!)
        assertTrue("sqlTypeOverride" in ex.message!!)
    }
}
