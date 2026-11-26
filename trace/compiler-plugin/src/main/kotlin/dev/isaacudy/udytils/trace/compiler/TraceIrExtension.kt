package dev.isaacudy.udytils.trace.compiler

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
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
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrCatchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
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
private val ENTER = CallableId(RUNTIME, Name.identifier("traceEnter"))
private val THREW = CallableId(RUNTIME, Name.identifier("traceThrew"))
private val EXIT = CallableId(RUNTIME, Name.identifier("traceExit"))
private val NOT_TRACED = FqName("dev.isaacudy.udytils.trace.NotTraced")
private val COMPOSABLE = FqName("androidx.compose.runtime.Composable")

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
            val finder = pluginContext.finderForSource(file)
            val probes = Probes(
                siteConstructor = finder.findConstructors(TRACE_SITE).singleOrNull()
                    ?: error("$PLUGIN_ID found no ${TRACE_SITE.asSingleFqName()}. Add dev.isaacudy.udytils:trace-runtime to the compilation."),
                enter = finder.findFunctions(ENTER).single(),
                threw = finder.findFunctions(THREW).single(),
                exit = finder.findFunctions(EXIT).single(),
                siteType = finder.findClass(TRACE_SITE)!!.defaultType,
            )
            val instrumenter = FileInstrumenter(file, pluginContext, probes, ::isIncluded)
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
    val enter: IrSimpleFunctionSymbol,
    val threw: IrSimpleFunctionSymbol,
    val exit: IrSimpleFunctionSymbol,
    val siteType: IrType,
)

/**
 * Wraps each eligible function body, and each `fun interface` lambda body, in
 * `traceEnter`/`traceThrew`/`traceExit`, with one static [Site] field per body.
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
    private val occurrences = mutableMapOf<String, Int>()
    private val fileKey = "${file.packageFqName}/${File(file.fileEntry.name).name}"

    override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
        val name = when (declaration) {
            is IrField -> declaration.kotlinFqName.asString()
            is IrAnonymousInitializer -> "${(declaration.parent as? IrClass)?.kotlinFqName}.<init>"
            else -> null
        } ?: return super.visitDeclaration(declaration)
        return within(name) { super.visitDeclaration(declaration) }
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) return super.visitFunction(declaration)
        val result = within(declaration.signature()) { super.visitFunction(declaration) }
        if (declaration is IrSimpleFunction && declaration.isEligible()) {
            val root = declaration.root()
            val kind = if (root === declaration) "function" else "override"
            instrument(declaration, root.signature(), declaration.kotlinFqName.asString(), declaration.signature(), kind)
        }
        return result
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
        expression.transformChildrenVoid(this)
        if (expression.operator != IrTypeOperator.SAM_CONVERSION) return expression
        val lambda = (expression.argument as? IrFunctionExpression)?.function ?: return expression
        val samInterface = expression.typeOperand.classOrNull?.owner ?: return expression
        if (!samInterface.isFun || !isIncluded(samInterface.packageFqName?.asString().orEmpty())) return expression
        if (lambda.hasAnnotation(COMPOSABLE) || lambda.isInline) return expression
        val method = samInterface.declarations.filterIsInstance<IrSimpleFunction>().singleOrNull { it.modality == org.jetbrains.kotlin.descriptors.Modality.ABSTRACT }
            ?: return expression
        val owner = enclosing.lastOrNull() ?: return expression
        instrument(lambda, method.signature(), owner, "$owner|sam:${samInterface.kotlinFqName}", "sam")
        return expression
    }

    private fun instrument(function: IrSimpleFunction, declarationSignature: String, name: String, siteKey: String, kind: String) {
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
        wrapBody(function, siteGetter(site, properties.size))
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

    private fun wrapBody(function: IrSimpleFunction, siteGetter: IrSimpleFunction) {
        val body = function.body ?: return
        val builder = DeclarationIrBuilder(context, function.symbol, function.startOffset, function.endOffset)
        val statements: List<IrStatement> = when (body) {
            is IrBlockBody -> body.statements.toList()
            is IrExpressionBody -> listOf(builder.irReturn(body.expression))
            else -> return
        }
        val returnType = function.returnType
        val nothing = context.irBuiltIns.nothingType
        function.body = builder.irBlockBody {
            val span = irTemporary(
                irCall(probes.enter).apply { arguments[0] = irCall(siteGetter) },
                nameHint = "traceSpan",
            )
            val error = buildVariable(
                function, startOffset, endOffset, IrDeclarationOrigin.CATCH_PARAMETER,
                Name.identifier("traceError"), context.irBuiltIns.throwableType,
            )
            val catch = IrCatchImpl(
                startOffset, endOffset, error,
                irBlock(resultType = nothing) {
                    +irCall(probes.threw).apply {
                        arguments[0] = irGet(span)
                        arguments[1] = irGet(error)
                    }
                    +IrThrowImpl(startOffset, endOffset, nothing, irGet(error))
                },
            )
            val tryBody = irBlock(resultType = returnType) { statements.forEach { +it } }
            val finally = irCall(probes.exit).apply { arguments[0] = irGet(span) }
            +irReturn(irTry(returnType, tryBody, listOf(catch), finally))
        }
    }

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
