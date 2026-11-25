package dev.isaacudy.udytils.mutation.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File

private val FLIP = CallableId(FqName("dev.isaacudy.udytils.mutation"), Name.identifier("mutantFlip"))

const val NEGATE_CONDITION = "negate-condition"
const val NEGATE_BOOLEAN_RETURN = "negate-boolean-return"

class MutationIrExtension(
    private val manifestDir: String,
    private val includedPackages: List<String>,
    private val excludedPaths: List<String> = emptyList(),
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        for (file in moduleFragment.files) {
            if (!isIncluded(file.packageFqName.asString())) continue
            val path = file.fileEntry.name
            if (excludedPaths.any { path.startsWith(it.trimEnd('/') + "/") }) continue
            val source = File(path).takeIf { it.isFile }?.readText() ?: continue
            val flip = pluginContext.finderForSource(file).findFunctions(FLIP).singleOrNull()
                ?: error("$PLUGIN_ID found no ${FLIP.asSingleFqName()}. Add dev.isaacudy.udytils:mutation-runtime to the compilation.")
            val mutator = FileMutator(file, File(path).name, source, flip, pluginContext)
            file.transformChildrenVoid(mutator)
            writeManifest(path, file.packageFqName.asString(), mutator.mutants)
        }
    }

    private fun isIncluded(pkg: String) =
        includedPackages.isEmpty() || includedPackages.any { pkg == it || pkg.startsWith("$it.") }

    private fun writeManifest(path: String, packageName: String, mutants: List<Mutant>) {
        val dir = File(manifestDir).apply { mkdirs() }
        File(dir, "${java.lang.Long.toHexString(stableHash(path))}.json").writeText(buildString {
            append("{\"file\":").append(jsonString(path)).append(",\"package\":").append(jsonString(packageName)).append(",\"mutants\":[")
            mutants.joinTo(this, ",") {
                "{\"id\":${it.id},\"line\":${it.line},\"operator\":\"${it.operator}\"," +
                    "\"declaration\":${jsonString(it.declaration)},\"text\":${jsonString(it.text)},\"initScope\":${it.initScope}}"
            }
            append("]}")
        })
    }
}

class Mutant(
    val id: Long,
    val line: Int,
    val operator: String,
    val declaration: String,
    val text: String,
    val initScope: Boolean,
)

/** A declaration whose body contains mutant sites: a function, a property initialiser or an `init` block. */
private class Scope(val owner: IrSymbolOwner, val signature: String, val initScope: Boolean)

private class FileMutator(
    private val file: IrFile,
    private val fileName: String,
    private val source: String,
    private val flip: IrSimpleFunctionSymbol,
    private val context: IrPluginContext,
) : IrElementTransformerVoid() {
    val mutants = mutableListOf<Mutant>()
    private val scopes = ArrayDeque<Scope>()
    private val occurrences = mutableMapOf<String, Int>()

    override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
        // K2 moves a reordered named argument into a temporary variable whose origin is not DEFINED.
        if (declaration is IrVariable) return super.visitDeclaration(declaration)
        if (!declaration.isSource()) return declaration
        val scope = when (declaration) {
            is IrField -> Scope(declaration, declaration.kotlinFqName.asString(), declaration.runsOncePerProcess())
            is IrAnonymousInitializer -> {
                val parent = declaration.parent as? IrClass
                Scope(declaration, "${parent?.kotlinFqName}.<init>", parent?.isObject == true)
            }
            else -> null
        } ?: return super.visitDeclaration(declaration)
        return withScope(scope) { super.visitDeclaration(declaration) }
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.isSource()) return declaration
        if (declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) return super.visitFunction(declaration)
        val signature = declaration.kotlinFqName.asString() +
            declaration.parameters.joinToString(",", "(", ")") { it.type.render() }
        return withScope(Scope(declaration, signature, initScope = false)) { super.visitFunction(declaration) }
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        expression.transformChildrenVoid(this)
        if (expression.origin != IrStatementOrigin.IF && expression.origin != IrStatementOrigin.WHEN) return expression
        for (branch in expression.branches) {
            if (branch is IrElseBranch) continue
            branch.condition = wrap(branch.condition, NEGATE_CONDITION) ?: continue
        }
        return expression
    }

    override fun visitLoop(loop: IrLoop): IrExpression {
        loop.transformChildrenVoid(this)
        val isSourceLoop = (loop is IrWhileLoop && loop.origin == IrStatementOrigin.WHILE_LOOP) ||
            (loop is IrDoWhileLoop && loop.origin == IrStatementOrigin.DO_WHILE_LOOP)
        if (isSourceLoop) loop.condition = wrap(loop.condition, NEGATE_CONDITION) ?: loop.condition
        return loop
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        expression.transformChildrenVoid(this)
        val target = expression.returnTargetSymbol.owner as? IrSimpleFunction ?: return expression
        if (target.origin != IrDeclarationOrigin.DEFINED || !expression.value.type.isBoolean()) return expression
        expression.value = wrap(expression.value, NEGATE_BOOLEAN_RETURN) ?: return expression
        return expression
    }

    private fun <T> withScope(scope: Scope, block: () -> T): T {
        scopes.addLast(scope)
        try {
            return block()
        } finally {
            scopes.removeLast()
        }
    }

    private fun wrap(value: IrExpression, operator: String): IrExpression? {
        val scope = scopes.lastOrNull() ?: return null
        if (value.startOffset < 0 || value.endOffset > source.length || value.endOffset <= value.startOffset) return null
        val text = source.substring(value.startOffset, value.endOffset).replace(WHITESPACE, " ").trim()
        val key = "${scope.signature}|$fileName|$operator|$text"
        val occurrence = occurrences.merge(key, 1, Int::plus)!!
        val id = stableHash("$key|$occurrence").let { if (it == 0L) 1L else it }
        mutants += Mutant(
            id = id,
            line = file.fileEntry.getLineNumber(value.startOffset) + 1,
            operator = operator,
            declaration = scope.signature,
            text = text,
            initScope = scope.initScope,
        )
        val builder = DeclarationIrBuilder(context, scope.owner.symbol, value.startOffset, value.endOffset)
        return builder.irCall(flip).apply {
            arguments[0] = builder.irLong(id)
            arguments[1] = value
        }
    }

    private fun IrDeclaration.isSource() =
        origin == IrDeclarationOrigin.DEFINED || origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA ||
            origin == IrDeclarationOrigin.PROPERTY_BACKING_FIELD || origin == IrDeclarationOrigin.PROPERTY_DELEGATE

    /** A top-level or `object` property initialiser runs once per process. */
    private fun IrField.runsOncePerProcess() = isStatic || parent is IrFile || (parent as? IrClass)?.isObject == true
}

private val WHITESPACE = Regex("\\s+")

/** FNV-1a, 64 bit, positive. */
internal fun stableHash(text: String): Long {
    var hash = -0x340d631b7bdddcdbL
    for (byte in text.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toLong() and 0xff)
        hash *= 0x100000001b3L
    }
    return hash and Long.MAX_VALUE
}

private fun jsonString(s: String) = buildString {
    append('"')
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c < ' ' -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
    append('"')
}
