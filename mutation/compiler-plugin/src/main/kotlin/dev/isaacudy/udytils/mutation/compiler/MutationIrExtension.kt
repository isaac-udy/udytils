package dev.isaacudy.udytils.mutation.compiler

import dev.isaacudy.udytils.mutation.compiler.operators.MutationOperator
import dev.isaacudy.udytils.mutation.compiler.operators.MutationOperators
import dev.isaacudy.udytils.mutation.compiler.operators.Site
import dev.isaacudy.udytils.mutation.compiler.operators.SiteKind
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irWhen
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
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File

private val ACTIVE = CallableId(FqName("dev.isaacudy.udytils.mutation"), Name.identifier("mutantActive"))

/** The longest mutant text a manifest stores; the mutant ID hashes the full text. */
private const val MAX_TEXT = 160

class MutationIrExtension(
    private val manifestDir: String,
    private val includedPackages: List<String>,
    private val excludedPaths: List<String> = emptyList(),
    private val operators: List<MutationOperator> = MutationOperators.defaults,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        for (file in moduleFragment.files) {
            if (!isIncluded(file.packageFqName.asString())) continue
            val path = file.fileEntry.name
            if (excludedPaths.any { path.startsWith(it.trimEnd('/') + "/") }) continue
            val source = File(path).takeIf { it.isFile }?.readText() ?: continue
            val finder = pluginContext.finderForSource(file)
            val active = finder.findFunctions(ACTIVE).singleOrNull()
                ?: error("$PLUGIN_ID found no ${ACTIVE.asSingleFqName()}. Add dev.isaacudy.udytils:mutation-runtime to the compilation.")
            val mutator = FileMutator(file, File(path).name, source, active, pluginContext, operators) { finder.findFunctions(it) }
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
                "{\"id\":${it.id},\"line\":${it.line},\"operator\":\"${it.operator}\",\"variant\":${jsonString(it.variant)}," +
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
    val variant: String,
    val declaration: String,
    val text: String,
    val initScope: Boolean,
)

/** A declaration whose body contains mutant sites: a function, a property initialiser or an `init` block. */
private class Scope(val owner: IrSymbolOwner, val signature: String, val initScope: Boolean)

/** A mutant and its replacement, created before the site's children are mutated. */
private class Planned(val id: Long, val replacement: IrExpression)

/**
 * Offers each site to the [operators] and wraps the site in a `when` over the mutants' switches.
 * Each site's mutations are planned before its children are transformed, so a replacement holds
 * the original children and only the `else` branch holds the children's own mutants.
 */
private class FileMutator(
    private val file: IrFile,
    private val fileName: String,
    private val source: String,
    private val active: IrSimpleFunctionSymbol,
    private val context: IrPluginContext,
    operators: List<MutationOperator>,
    private val functions: (CallableId) -> Collection<IrSimpleFunctionSymbol>,
) : IrElementTransformerVoid() {
    val mutants = mutableListOf<Mutant>()
    private val scopes = ArrayDeque<Scope>()
    private val occurrences = mutableMapOf<String, Int>()
    private val operatorsByKind = SiteKind.entries.associateWith { kind -> operators.filter { kind in it.siteKinds } }

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

    override fun visitExpression(expression: IrExpression): IrExpression =
        mutate(expression) { it.transformChildrenVoid(this) }

    override fun visitWhen(expression: IrWhen): IrExpression = mutate(expression) { whenExpression ->
        val isSource = whenExpression.origin == IrStatementOrigin.IF || whenExpression.origin == IrStatementOrigin.WHEN
        val conditions = if (!isSource) emptyMap() else whenExpression.branches.filter { it !is IrElseBranch }
            .associateWith { plan(SiteKind.Condition, it.condition) }
        whenExpression.transformChildrenVoid(this)
        conditions.forEach { (branch, planned) -> branch.condition = wrap(branch.condition, planned) }
    }

    override fun visitLoop(loop: IrLoop): IrExpression = mutate(loop) {
        val isSource = (loop is IrWhileLoop && loop.origin == IrStatementOrigin.WHILE_LOOP) ||
            (loop is IrDoWhileLoop && loop.origin == IrStatementOrigin.DO_WHILE_LOOP)
        val planned = if (isSource) plan(SiteKind.Condition, loop.condition) else emptyList()
        loop.transformChildrenVoid(this)
        loop.condition = wrap(loop.condition, planned)
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        val target = expression.returnTargetSymbol.owner as? IrSimpleFunction
        val planned = if (target?.origin == IrDeclarationOrigin.DEFINED) plan(SiteKind.ReturnValue, expression.value, target.returnType) else emptyList()
        expression.transformChildrenVoid(this)
        expression.value = wrap(expression.value, planned)
        return expression
    }

    override fun visitBlockBody(body: IrBlockBody): IrBody {
        val planned = planStatements(body.statements, lastIsValue = false)
        body.transformChildrenVoid(this)
        wrapStatements(body.statements, planned)
        return body
    }

    override fun visitContainerExpression(expression: IrContainerExpression): IrExpression = mutate(expression) { container ->
        val planned = if (container is IrBlock && container.origin == null) {
            planStatements(container.statements, lastIsValue = !container.type.isUnit())
        } else {
            emptyMap()
        }
        container.transformChildrenVoid(this)
        wrapStatements(container.statements, planned)
    }

    private fun <T : IrExpression> mutate(expression: T, transformChildren: (T) -> Unit): IrExpression {
        val planned = plan(SiteKind.Expression, expression)
        transformChildren(expression)
        return wrap(expression, planned)
    }

    private fun planStatements(statements: List<IrStatement>, lastIsValue: Boolean): Map<Int, List<Planned>> =
        statements.withIndex()
            .filter { (index, statement) -> statement is IrExpression && !(lastIsValue && index == statements.lastIndex) }
            .associate { (index, statement) -> index to plan(SiteKind.Statement, statement as IrExpression) }

    private fun wrapStatements(statements: MutableList<IrStatement>, planned: Map<Int, List<Planned>>) {
        for ((index, mutations) in planned) {
            if (mutations.isEmpty()) continue
            val statement = statements[index] as IrExpression
            val unit = context.irBuiltIns.unitType
            val discarded = if (statement.type.isUnit()) statement else {
                IrTypeOperatorCallImpl(statement.startOffset, statement.endOffset, unit, IrTypeOperator.IMPLICIT_COERCION_TO_UNIT, unit, statement)
            }
            statements[index] = wrap(discarded, mutations)
        }
    }

    private fun plan(kind: SiteKind, expression: IrExpression, returnType: IrType? = null): List<Planned> {
        val operators = operatorsByKind.getValue(kind)
        if (operators.isEmpty()) return emptyList()
        val scope = scopes.lastOrNull() ?: return emptyList()
        // An implicit coercion to Unit can span less source text than the call it wraps.
        val located = (expression as? IrTypeOperatorCall)?.takeIf { it.operator == IrTypeOperator.IMPLICIT_COERCION_TO_UNIT }?.argument ?: expression
        val start = located.sourceStart()
        if (start < 0 || located.endOffset > source.length || located.endOffset <= start) return emptyList()
        val text = source.substring(start, located.endOffset).replace(WHITESPACE, " ").trim()
        val builder = DeclarationIrBuilder(context, scope.owner.symbol, start, located.endOffset)
        val site = Site(kind, expression, text, builder, returnType, functions, ::copy)
        val line = file.fileEntry.getLineNumber(start) + 1
        return operators.flatMap { operator ->
            operator.mutations(site).map { mutation ->
                val key = "${scope.signature}|$fileName|${operator.id}|${mutation.variant}|$text"
                val occurrence = occurrences.merge(key, 1, Int::plus)!!
                val id = stableHash("$key|$occurrence").let { if (it == 0L) 1L else it }
                mutants += Mutant(id, line, operator.id, mutation.variant, scope.signature, text.take(MAX_TEXT), scope.initScope)
                Planned(id, mutation.replacement)
            }
        }
    }

    private fun wrap(original: IrExpression, planned: List<Planned>): IrExpression {
        if (planned.isEmpty()) return original
        val builder = DeclarationIrBuilder(context, scopes.last().owner.symbol, original.startOffset, original.endOffset)
        val branches = planned.map { mutant ->
            builder.irBranch(builder.irCall(active).apply { arguments[0] = builder.irLong(mutant.id) }, mutant.replacement)
        }
        return builder.irWhen(original.type, branches + builder.irElseBranch(original))
    }

    /** The start of the expression's source text: a call's offsets leave out its receiver. */
    private fun IrExpression.sourceStart(): Int {
        val arguments = (this as? IrMemberAccessExpression<*>)?.arguments.orEmpty().filterNotNull()
        return (arguments.map { it.sourceStart() }.filter { it >= 0 } + startOffset).min()
    }

    /** Copies [expression] with its local declarations, which keep the original parent. */
    private fun copy(expression: IrExpression): IrExpression {
        val parent = expression.firstLocalDeclaration()?.parent
        return expression.deepCopyWithSymbols(parent)
    }

    private fun IrExpression.firstLocalDeclaration(): IrDeclaration? {
        var found: IrDeclaration? = null
        acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (found == null) element.acceptChildrenVoid(this)
            }

            override fun visitDeclaration(declaration: IrDeclarationBase) {
                if (found == null) found = declaration
            }
        })
        return found
    }

    private fun <T> withScope(scope: Scope, block: () -> T): T {
        scopes.addLast(scope)
        try {
            return block()
        } finally {
            scopes.removeLast()
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
