package dev.isaacudy.udytils.trace.compiler

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addBackingField
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrRichFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrCatchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File

private val RUNTIME = FqName("dev.isaacudy.udytils.trace")
private val TRACE_SITE = ClassId(RUNTIME, Name.identifier("TraceSite"))
private val SPAN = ClassId(RUNTIME, Name.identifier("Span"))
private val NOT_TRACED = FqName("dev.isaacudy.udytils.trace.NotTraced")
private val COMPOSABLE = FqName("androidx.compose.runtime.Composable")
private val COROUTINE_CONTEXT = CallableId(FqName("kotlin.coroutines"), Name.identifier("coroutineContext"))
private const val DECLARATION_ID_OF = "dev.isaacudy.udytils.trace.declarationIdOf"

/** Calls whose `context` argument receives the current span, so the coroutine they start inherits it. */
private val CONTEXT_CALLS = setOf(
    "kotlinx.coroutines.launch",
    "kotlinx.coroutines.async",
    "kotlinx.coroutines.withContext",
    "kotlinx.coroutines.flow.flowOn",
)

private object TraceKey : GeneratedDeclarationKey()

class TraceIrExtension(
    private val manifestDir: String,
    private val includedPackages: List<String>,
    private val excludedPaths: List<String> = emptyList(),
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        for (file in moduleFragment.files) {
            val packageName = file.packageFqName.asString()
            if (!isIncluded(packageName)) continue
            val path = file.fileEntry.name
            if (excludedPaths.any { path.startsWith(it.trimEnd('/') + "/") }) continue
            if (file.hasAnnotation(NOT_TRACED)) continue
            val instrumenter = FileInstrumenter(file, pluginContext, Probes.find(pluginContext, file), ::isIncluded)
            file.transformChildrenVoid(instrumenter)
            file.declarations += instrumenter.properties
            writeManifest(path, packageName, instrumenter.sites)
        }
    }

    private fun isIncluded(pkg: String) =
        if (includedPackages.isEmpty()) !pkg.startsWith("kotlin") else includedPackages.any { pkg == it || pkg.startsWith("$it.") }

    private fun writeManifest(path: String, packageName: String, sites: List<Site>) {
        val dir = File(manifestDir).apply { mkdirs() }
        val manifest = File(dir, "${java.lang.Long.toHexString(stableHash(path))}.json")
        if (sites.isEmpty()) {
            manifest.delete()
            return
        }
        manifest.writeText(buildString {
            append("{\"file\":").append(jsonString(path)).append(",\"package\":").append(jsonString(packageName)).append(",\"sites\":[")
            sites.joinTo(this, ",") {
                "{\"siteId\":${it.siteId},\"declarationId\":${it.declarationId},\"name\":${jsonString(it.name)}," +
                    "\"declaration\":${jsonString(it.declaration)},\"kind\":\"${it.kind}\",\"line\":${it.line},\"suspend\":${it.suspend}}"
            }
            append("]}")
        })
    }
}

class Site(
    val siteId: Long,
    val declarationId: Long,
    val name: String,
    val declaration: String,
    val kind: String,
    val line: Int,
    val suspend: Boolean,
)

private class Probes(
    val siteConstructor: IrConstructorSymbol,
    val siteType: IrType,
    val spanType: IrType,
    val coroutineContext: IrSimpleFunctionSymbol,
    private val functions: Map<String, IrSimpleFunctionSymbol>,
) {
    operator fun get(name: String): IrSimpleFunctionSymbol = functions.getValue(name)

    companion object {
        private val NAMES = listOf(
            "traceEnter", "traceThrew", "traceExit", "traceAdopt", "traceEnterSuspend",
            "traceResume", "traceSuspend", "traceEndSuspend", "traceContext", "traceFlow",
        )

        fun find(context: IrPluginContext, file: IrFile): Probes {
            val finder = context.finderForSource(file)
            val site = finder.findClass(TRACE_SITE)
                ?: error("$PLUGIN_ID found no ${TRACE_SITE.asSingleFqName()}. Add dev.isaacudy.udytils:trace-runtime to the compilation.")
            return Probes(
                siteConstructor = finder.findConstructors(TRACE_SITE).single(),
                siteType = site.defaultType,
                spanType = finder.findClass(SPAN)!!.defaultType.makeNullable(),
                coroutineContext = finder.findProperties(COROUTINE_CONTEXT).single().owner.getter!!.symbol,
                functions = NAMES.associateWith { finder.findFunctions(CallableId(RUNTIME, Name.identifier(it))).single() },
            )
        }
    }
}

/** The span locals of a suspend body; each suspend call in the body hands them to the callee. */
private class Propagation(val parent: IrVariable, val span: IrVariable)

private class Body(val owner: IrSymbol, val propagation: Propagation?)

private class SamSite(val declarationSignature: String, val name: String, val siteKey: String)

/**
 * Wraps each eligible function body and `fun interface` lambda body in span probes, gives every
 * suspend body in the file the propagation probes, and passes the current span to the coroutines
 * that [CONTEXT_CALLS] start.
 */
private class FileInstrumenter(
    private val file: IrFile,
    private val context: IrPluginContext,
    private val probes: Probes,
    private val isIncluded: (String) -> Boolean,
) : IrElementTransformerVoid() {
    val sites = mutableListOf<Site>()
    val properties = mutableListOf<IrProperty>()
    private val enclosing = ArrayDeque<String>()
    private val bodies = ArrayDeque<Body>()
    private val samSites = mutableMapOf<IrSimpleFunction, SamSite>()
    private val occurrences = mutableMapOf<String, Int>()
    private val fileKey = "${file.packageFqName}/${File(file.fileEntry.name).name}"

    override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
        val name = when (declaration) {
            is IrField -> declaration.kotlinFqName.asString()
            is IrAnonymousInitializer -> "${(declaration.parent as? IrClass)?.kotlinFqName}.<init>"
            else -> null
        } ?: return super.visitDeclaration(declaration)
        bodies.addLast(Body(declaration.symbol, null))
        try {
            return within(name) { super.visitDeclaration(declaration) }
        } finally {
            bodies.removeLast()
        }
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration !is IrSimpleFunction) return super.visitFunction(declaration)
        val isLambda = declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        val propagation = if (declaration.isPropagated()) {
            Propagation(parent = variable(declaration, "traceParent"), span = variable(declaration, "traceSpan"))
        } else {
            null
        }
        // A non-suspend lambda that contains suspend calls is passed to an inline function and runs in the enclosing suspend body.
        val inherited = if (isLambda && !declaration.isSuspend) bodies.lastOrNull()?.propagation else null
        bodies.addLast(Body(declaration.symbol, propagation ?: inherited))
        val result = try {
            if (isLambda) super.visitFunction(declaration) else within(declaration.signature()) { super.visitFunction(declaration) }
        } finally {
            bodies.removeLast()
        }
        val sam = samSites[declaration]
        val returnsFlow = declaration.returnsFlow()
        val siteGetter = when {
            sam != null -> register(declaration, sam.declarationSignature, sam.name, sam.siteKey, if (returnsFlow) "flow" else "sam")
            !isLambda && declaration.isEligible() -> {
                val root = declaration.root()
                val kind = if (returnsFlow) "flow" else if (root === declaration) "function" else "override"
                register(declaration, root.signature(), declaration.kotlinFqName.asString(), declaration.signature(), kind)
            }
            else -> null
        }
        when {
            propagation != null -> wrapSuspendBody(declaration, propagation, siteGetter)
            siteGetter != null && returnsFlow -> wrapReturnedFlows(declaration, siteGetter)
            siteGetter != null -> wrapBody(declaration, siteGetter)
        }
        return result
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
        if (expression.operator == IrTypeOperator.SAM_CONVERSION) {
            val lambda = (expression.argument as? IrFunctionExpression)?.function
            val samInterface = expression.typeOperand.classOrNull?.owner
            val method = samInterface?.takeIf { it.isFun && isIncluded(it.packageFqName?.asString().orEmpty()) }
                ?.declarations?.filterIsInstance<IrSimpleFunction>()?.singleOrNull { it.modality == Modality.ABSTRACT }
            val owner = enclosing.lastOrNull()
            if (lambda != null && method != null && owner != null && !lambda.hasAnnotation(COMPOSABLE) && !lambda.isInline) {
                samSites[lambda] = SamSite(method.signature(), owner, "$owner|sam:${samInterface.kotlinFqName}")
            }
        }
        expression.transformChildrenVoid(this)
        return expression
    }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid(this)
        val body = bodies.lastOrNull() ?: return expression
        val callee = expression.symbol.owner
        val name = callee.kotlinFqName.asString()
        if (name == DECLARATION_ID_OF) return declarationIdOf(expression, body)
        if (name in CONTEXT_CALLS) addCurrentSpan(expression, callee, body)
        val propagation = body.propagation
        if (!callee.isSuspend || propagation == null) return expression
        return handOff(expression, propagation, body)
    }

    /** `f(a, b)` becomes `{ val t0 = a; val t1 = b; traceSuspend(…); val r = f(t0, t1); traceResume(…); r }`. */
    private fun handOff(call: IrCall, propagation: Propagation, body: Body): IrExpression {
        val builder = DeclarationIrBuilder(context, body.owner, call.startOffset, call.endOffset)
        return builder.irBlock(resultType = call.type) {
            for (index in call.arguments.indices) {
                val argument = call.arguments[index] ?: continue
                if (argument.isEvaluatedInPlace()) continue
                call.arguments[index] = irGet(irTemporary(argument, nameHint = "traceArgument"))
            }
            +irCall(probes["traceSuspend"]).apply {
                arguments[0] = irGet(propagation.span)
                arguments[1] = irGet(propagation.parent)
                arguments[2] = irCall(probes.coroutineContext)
            }
            if (call.type.isNothing()) {
                +call
            } else {
                val result = irTemporary(call, nameHint = "traceResult")
                +resume(propagation)
                +irGet(result)
            }
        }
    }

    private fun addCurrentSpan(call: IrCall, callee: IrSimpleFunction, body: Body) {
        val index = callee.parameters.indexOfFirst { it.name.asString() == "context" && it.kind == IrParameterKind.Regular }
        if (index < 0) return
        val builder = DeclarationIrBuilder(context, body.owner, call.startOffset, call.endOffset)
        val existing = call.arguments[index]
        call.arguments[index] = builder.irCall(probes["traceContext"]).apply {
            arguments[0] = existing ?: builder.irNull()
        }
    }

    private fun declarationIdOf(call: IrCall, body: Body): IrExpression {
        var reference = call.arguments.firstOrNull()
        while (reference is IrTypeOperatorCall) reference = reference.argument
        val target = when (reference) {
            is IrRichFunctionReference -> reference.reflectionTargetSymbol?.owner
            is IrFunctionReference -> (reference.reflectionTarget ?: reference.symbol).owner
            else -> null
        } as? IrSimpleFunction ?: error("$DECLARATION_ID_OF requires a function reference, such as Greet::invoke, in ${file.fileEntry.name}")
        return DeclarationIrBuilder(context, body.owner, call.startOffset, call.endOffset)
            .irLong(nonZeroHash(target.root().signature()))
    }

    private fun IrBuilderWithScope.resume(propagation: Propagation) = irCall(probes["traceResume"]).apply {
        arguments[0] = irGet(propagation.span)
        arguments[1] = irGet(propagation.parent)
    }

    private fun register(function: IrSimpleFunction, declarationSignature: String, name: String, siteKey: String, kind: String): IrSimpleFunction {
        val key = "$fileKey|$siteKey"
        val occurrence = occurrences.merge(key, 1, Int::plus)!!
        val site = Site(
            siteId = nonZeroHash("$key|$occurrence"),
            declarationId = nonZeroHash(declarationSignature),
            name = name,
            declaration = declarationSignature,
            kind = kind,
            line = file.fileEntry.getLineNumber(function.startOffset) + 1,
            suspend = function.isSuspend,
        )
        sites += site
        return siteGetter(site, properties.size)
    }

    /** A private top-level property, read through its getter: the Native, JS and wasm backends initialize it lazily in the getter. */
    private fun siteGetter(site: Site, index: Int): IrSimpleFunction {
        val name = Name.identifier("\$traceSite\$$index")
        val property = context.irFactory.buildProperty {
            this.name = name
            visibility = DescriptorVisibilities.PRIVATE
            origin = IrDeclarationOrigin.GeneratedByPlugin(TraceKey)
        }
        property.parent = file
        val field = property.addBackingField {
            this.name = name
            type = probes.siteType
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = true
            isStatic = true
            origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
        }
        properties += property
        val builder = DeclarationIrBuilder(context, field.symbol)
        field.initializer = builder.irExprBody(
            builder.irCallConstructor(probes.siteConstructor, emptyList()).apply {
                arguments[0] = builder.irLong(site.siteId)
                arguments[1] = builder.irLong(site.declarationId)
                arguments[2] = builder.irString(site.name)
            },
        )
        val getter = property.addGetter {
            returnType = probes.siteType
            visibility = DescriptorVisibilities.PRIVATE
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        getter.body = DeclarationIrBuilder(context, getter.symbol).irBlockBody { +irReturn(irGetField(null, field)) }
        return getter
    }

    private fun bodyStatements(function: IrSimpleFunction, builder: DeclarationIrBuilder): List<IrStatement>? =
        when (val body = function.body) {
            is IrBlockBody -> body.statements.toList()
            is IrExpressionBody -> listOf(builder.irReturn(body.expression))
            else -> null
        }

    /** Replaces each `return flow` of [function] with `return traceFlow(site, flow)`; the call itself records no span. */
    private fun wrapReturnedFlows(function: IrSimpleFunction, siteGetter: IrSimpleFunction) {
        val builder = DeclarationIrBuilder(context, function.symbol, function.startOffset, function.endOffset)
        (function.body as? IrExpressionBody)?.let { body -> function.body = builder.irBlockBody { +irReturn(body.expression) } }
        val valueType = (function.returnType as? IrSimpleType)?.arguments?.singleOrNull()?.typeOrNull
            ?: context.irBuiltIns.anyNType
        function.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitReturn(expression: IrReturn): IrExpression {
                expression.transformChildrenVoid(this)
                if (expression.returnTargetSymbol != function.symbol) return expression
                expression.value = builder.irCall(probes["traceFlow"], function.returnType).apply {
                    typeArguments[0] = valueType
                    arguments[0] = builder.irCall(siteGetter)
                    arguments[1] = expression.value
                }
                return expression
            }
        })
    }

    private fun IrSimpleFunction.returnsFlow(): Boolean =
        !isSuspend && returnType.classOrNull?.owner?.kotlinFqName?.asString() == "kotlinx.coroutines.flow.Flow"

    private fun wrapBody(function: IrSimpleFunction, siteGetter: IrSimpleFunction) {
        val builder = DeclarationIrBuilder(context, function.symbol, function.startOffset, function.endOffset)
        val statements = bodyStatements(function, builder) ?: return
        function.body = builder.irBlockBody {
            val span = irTemporary(irCall(probes["traceEnter"]).apply { arguments[0] = irCall(siteGetter) }, nameHint = "traceSpan")
            +irReturn(tracedTry(function, statements, span, probes["traceExit"]))
        }
    }

    /** Adopts the caller's span, opens the site's span, and makes the body's span current at entry. */
    private fun wrapSuspendBody(function: IrSimpleFunction, propagation: Propagation, siteGetter: IrSimpleFunction?) {
        val builder = DeclarationIrBuilder(context, function.symbol, function.startOffset, function.endOffset)
        val statements = bodyStatements(function, builder) ?: return
        function.body = builder.irBlockBody {
            propagation.parent.initializer = irCall(probes["traceAdopt"]).apply {
                arguments[0] = irCall(probes.coroutineContext)
                arguments[1] = siteGetter?.let { irCall(it) } ?: irNull()
            }
            +propagation.parent
            propagation.span.initializer = if (siteGetter == null) {
                irNull()
            } else {
                irCall(probes["traceEnterSuspend"]).apply {
                    arguments[0] = irCall(siteGetter)
                    arguments[1] = irGet(propagation.parent)
                }
            }
            +propagation.span
            +resume(propagation)
            +irReturn(tracedTry(function, statements, propagation.span, probes["traceEndSuspend"], propagation))
        }
    }

    private fun IrBuilderWithScope.tracedTry(
        function: IrSimpleFunction,
        statements: List<IrStatement>,
        span: IrVariable,
        end: IrSimpleFunctionSymbol,
        propagation: Propagation? = null,
    ): IrExpression {
        val nothing = context.irBuiltIns.nothingType
        val error = buildVariable(
            function, startOffset, endOffset, IrDeclarationOrigin.CATCH_PARAMETER,
            Name.identifier("traceError"), context.irBuiltIns.throwableType,
        )
        val catch = IrCatchImpl(
            startOffset, endOffset, error,
            irBlock(resultType = nothing) {
                +irCall(probes["traceThrew"]).apply {
                    arguments[0] = irGet(span)
                    arguments[1] = irGet(error)
                }
                +IrThrowImpl(startOffset, endOffset, nothing, irGet(error))
            },
        )
        val tryBody = irBlock(resultType = function.returnType) { statements.forEach { +it } }
        val finally = irCall(end).apply {
            arguments[0] = irGet(span)
            if (propagation != null) {
                arguments[1] = irGet(propagation.parent)
                arguments[2] = irCall(probes.coroutineContext)
            }
        }
        return irTry(function.returnType, tryBody, listOf(catch), finally)
    }

    private fun variable(function: IrSimpleFunction, name: String) = buildVariable(
        function, function.startOffset, function.endOffset, IrDeclarationOrigin.DEFINED,
        Name.identifier(name), probes.spanType,
    )

    private fun IrSimpleFunction.isPropagated(): Boolean =
        isSuspend && body != null && !isInline && !isTailrec && !hasAnnotation(NOT_TRACED) &&
            (origin == IrDeclarationOrigin.DEFINED || origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA)

    private fun IrSimpleFunction.isEligible(): Boolean {
        if (origin != IrDeclarationOrigin.DEFINED || body == null) return false
        if (isInline || isTailrec || isExpect || isExternal || correspondingPropertySymbol != null) return false
        if (visibility != DescriptorVisibilities.PUBLIC && visibility != DescriptorVisibilities.INTERNAL &&
            visibility != DescriptorVisibilities.PROTECTED
        ) {
            return false
        }
        if (hasAnnotation(COMPOSABLE) || hasAnnotation(NOT_TRACED)) return false
        var parent = parentClassOrNull
        while (parent != null) {
            if (parent.isLocal || parent.visibility == DescriptorVisibilities.PRIVATE || parent.hasAnnotation(NOT_TRACED)) return false
            parent = parent.parentClassOrNull
        }
        return root().parentClassOrNull?.kotlinFqName?.asString() != "kotlin.Any"
    }

    private fun IrSimpleFunction.root(): IrSimpleFunction {
        var function = this
        while (true) function = function.overriddenSymbols.firstOrNull()?.owner ?: return function
    }

    private fun <T> within(name: String, block: () -> T): T {
        enclosing.addLast(name)
        try {
            return block()
        } finally {
            enclosing.removeLast()
        }
    }
}

/** An argument whose evaluation the probes cannot observe, or a lambda that an inline callee requires in place. */
private fun IrExpression.isEvaluatedInPlace() =
    this is IrConst || this is IrGetValue || this is IrGetObjectValue || this is IrFunctionExpression ||
        this is IrRichFunctionReference || this is IrFunctionReference

internal fun IrFunction.signature(): String =
    kotlinFqName.asString() +
        parameters.filter { it.kind != IrParameterKind.DispatchReceiver }.joinToString(",", "(", ")") { it.type.render() }

private fun nonZeroHash(text: String) = stableHash(text).let { if (it == 0L) 1L else it }

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
