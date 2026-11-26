package dev.isaacudy.udytils.mutation.compiler.operators

import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Replaces a returned value with the empty value of the function's return type.
 *
 * | Return type | Replacement |
 * |---|---|
 * | any nullable type | `null` |
 * | `List`, `Collection`, `Iterable` | `emptyList()` |
 * | `Set` | `emptySet()` |
 * | `Map` | `emptyMap()` |
 * | `Sequence` | `emptySequence()` |
 * | `String` | `""` |
 * | `Int`, `Long` | `0` |
 *
 * `return repository.greetings()` → `return emptyList()`. A return value that is already the
 * empty value is skipped.
 */
object EmptyReturn : MutationOperator {
    override val id = "empty-return"
    override val description = "Replaces a returned value with null, an empty collection, an empty string or zero."
    override val siteKinds = setOf(SiteKind.ReturnValue)

    private val collections = mapOf(
        "kotlin.collections.List" to "kotlin.collections.emptyList",
        "kotlin.collections.Collection" to "kotlin.collections.emptyList",
        "kotlin.collections.Iterable" to "kotlin.collections.emptyList",
        "kotlin.collections.Set" to "kotlin.collections.emptySet",
        "kotlin.collections.Map" to "kotlin.collections.emptyMap",
        "kotlin.sequences.Sequence" to "kotlin.sequences.emptySequence",
    )

    override fun mutations(site: Site): List<Mutation> {
        val type = site.returnType ?: return emptyList()
        if (site.expression.type.isNothing()) return emptyList()
        val original = site.expression
        val b = site.builder
        val replacement = when {
            type.isMarkedNullable() -> if (original.isConst(null)) null else Mutation("→ null", b.irNull(type))
            type.isString() -> if (original.isConst("")) null else Mutation("→ \"\"", b.irString(""))
            type.isInt() -> if (original.isConst(0)) null else Mutation("→ 0", b.irInt(0))
            type.isLong() -> if (original.isConst(0L)) null else Mutation("→ 0", b.irLong(0))
            else -> emptyCollection(site, type)
        }
        return listOfNotNull(replacement)
    }

    private fun emptyCollection(site: Site, type: IrType): Mutation? {
        val factory = collections[type.classFqName?.asString()] ?: return null
        val name = FqName(factory)
        if ((site.expression as? IrCall)?.symbol?.owner?.kotlinFqName == name) return null
        val function = site.findFunctions(CallableId(name.parent(), Name.identifier(name.shortName().asString())))
            .singleOrNull { it.owner.parameters.isEmpty() } ?: return null
        val arguments = (type as? IrSimpleType)?.arguments.orEmpty()
        val call = site.builder.irCall(function, type).apply {
            for (i in typeArguments.indices) typeArguments[i] = arguments.getOrNull(i)?.typeOrNull ?: site.irBuiltIns.anyNType
        }
        return Mutation("→ ${name.shortName()}()", call)
    }

    private fun IrExpression.isConst(value: Any?) = this is IrConst && this.value == value
}
