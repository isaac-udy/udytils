package dev.isaacudy.udytils.urpc.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import java.io.OutputStreamWriter

private const val URPC_SERVICE_FQN = "dev.isaacudy.udytils.urpc.UrpcService"
private const val FLOW_FQN = "kotlinx.coroutines.flow.Flow"

class UrpcSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        val symbols = resolver.getSymbolsWithAnnotation(URPC_SERVICE_FQN).toList()

        for (symbol in symbols) {
            if (symbol !is KSClassDeclaration) {
                logger.error("@UrpcService can only be applied to interfaces", symbol)
                continue
            }
            if (symbol.classKind != ClassKind.INTERFACE) {
                logger.error("@UrpcService can only be applied to interfaces, not ${symbol.classKind}", symbol)
                continue
            }
            if (!symbol.validate()) {
                deferred += symbol
                continue
            }
            try {
                generate(symbol)
            } catch (t: Throwable) {
                logger.exception(t)
            }
        }
        return deferred
    }

    private fun generate(service: KSClassDeclaration) {
        val packageName = service.packageName.asString()
        val serviceName = service.simpleName.asString()
        val serviceWirePrefix = service.urpcServiceName() ?: serviceName.replaceFirstChar { it.lowercaseChar() }

        val functions = service.getAllFunctions()
            .filter { it.simpleName.asString() !in OBJECT_METHOD_NAMES }
            .toList()

        val descriptors = functions.mapNotNull { fn ->
            buildDescriptor(service, fn, serviceWirePrefix)
        }

        if (descriptors.isEmpty()) {
            logger.warn("@UrpcService interface $packageName.$serviceName has no urpc-eligible functions", service)
            return
        }

        writeGeneratedFile(service, descriptors)
    }

    private fun buildDescriptor(
        service: KSClassDeclaration,
        fn: KSFunctionDeclaration,
        wirePrefix: String,
    ): DescriptorModel? {
        val fnName = fn.simpleName.asString()
        val isSuspend = Modifier.SUSPEND in fn.modifiers

        if (fn.parameters.size > 1) {
            logger.error("urpc functions take 0 or 1 parameter. `$fnName` has ${fn.parameters.size}.", fn)
            return null
        }

        val paramType: KSType? = fn.parameters.firstOrNull()?.type?.resolve()
        val returnType: KSType = fn.returnType?.resolve()
            ?: error("Could not resolve return type of $fnName")

        val wireName = fn.urpcWireNameOverride()?.let { "$wirePrefix.$it" } ?: "$wirePrefix.$fnName"

        val paramIsFlow = paramType?.isFlow() == true
        val returnIsFlow = returnType.isFlow()

        return when {
            isSuspend -> {
                if (paramIsFlow) {
                    logger.error(
                        "`$fnName` is suspend but takes a Flow parameter — that combination isn't a valid urpc shape.",
                        fn,
                    )
                    return null
                }
                if (returnIsFlow) {
                    logger.error(
                        "`$fnName` is suspend but returns a Flow — for streaming use `fun $fnName(...): Flow<...>` (no suspend).",
                        fn,
                    )
                    return null
                }
                UnaryDescriptorModel(
                    functionName = fnName,
                    wireName = wireName,
                    requestTypeRef = paramType?.toTypeReference(),
                    responseTypeRef = returnType.toTypeReference(),
                )
            }

            returnIsFlow && paramIsFlow -> {
                val reqElement = paramType!!.flowElement()
                    ?: error("Could not resolve Flow element type of $fnName parameter")
                val resElement = returnType.flowElement()
                    ?: error("Could not resolve Flow element type of $fnName return")
                BidirectionalDescriptorModel(
                    functionName = fnName,
                    wireName = wireName,
                    requestTypeRef = reqElement.toTypeReference(),
                    responseTypeRef = resElement.toTypeReference(),
                )
            }

            returnIsFlow -> {
                val resElement = returnType.flowElement()
                    ?: error("Could not resolve Flow element type of $fnName return")
                StreamingDescriptorModel(
                    functionName = fnName,
                    wireName = wireName,
                    requestTypeRef = paramType?.toTypeReference(),
                    responseTypeRef = resElement.toTypeReference(),
                )
            }

            else -> {
                logger.error(
                    "`$fnName` on ${service.qualifiedName?.asString()} doesn't match any urpc shape. Use one of:\n" +
                            "  - unary:         suspend fun $fnName(req: Req): Res\n" +
                            "  - streaming:     fun $fnName(req: Req): Flow<Res>\n" +
                            "  - bidirectional: fun $fnName(reqs: Flow<Req>): Flow<Res>",
                    fn,
                )
                null
            }
        }
    }

    private fun writeGeneratedFile(
        service: KSClassDeclaration,
        descriptors: List<DescriptorModel>,
    ) {
        val packageName = service.packageName.asString()
        val serviceName = service.simpleName.asString()
        val serviceFqn = service.qualifiedName?.asString() ?: "$packageName.$serviceName"
        val fileName = "${serviceName}\$\$Urpc"

        val origin = service.containingFile
        val deps = if (origin != null) {
            Dependencies(aggregating = false, origin)
        } else {
            Dependencies(aggregating = false)
        }

        codeGenerator.createNewFile(
            dependencies = deps,
            packageName = packageName,
            fileName = fileName,
            extensionName = "kt",
        ).use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.appendLine("@file:Suppress(\"UNUSED_PARAMETER\", \"RedundantSuppression\", \"unused\")")
                writer.appendLine()
                writer.appendLine("package $packageName")
                writer.appendLine()
                // Always import all three descriptor types and Flow so we don't have
                // to introspect which combinations the service uses. Unused imports
                // are suppressed by the file-level @Suppress above.
                writer.appendLine("import dev.isaacudy.udytils.urpc.BidirectionalServiceDescriptor")
                writer.appendLine("import dev.isaacudy.udytils.urpc.ServiceDescriptor")
                writer.appendLine("import dev.isaacudy.udytils.urpc.StreamingServiceDescriptor")
                writer.appendLine("import dev.isaacudy.udytils.urpc.UrpcClientFactory")
                writer.appendLine("import dev.isaacudy.udytils.urpc.UrpcRoute")
                writer.appendLine("import kotlinx.coroutines.flow.Flow")
                writer.appendLine("import kotlinx.serialization.serializer")
                writer.appendLine()

                writer.appendLine("@PublishedApi")
                writer.appendLine("internal object ${serviceName}Descriptors {")
                for (d in descriptors) {
                    when (d) {
                        is UnaryDescriptorModel -> writeUnaryDescriptor(writer, d)
                        is StreamingDescriptorModel -> writeStreamingDescriptor(writer, d)
                        is BidirectionalDescriptorModel -> writeBidirectionalDescriptor(writer, d)
                    }
                }
                writer.appendLine("}")
                writer.appendLine()

                writer.appendLine("@PublishedApi")
                writer.appendLine("internal class ${serviceName}UrpcClient(")
                writer.appendLine("    private val urpc: UrpcClientFactory,")
                writer.appendLine(") : $serviceFqn {")
                for (d in descriptors) {
                    when (d) {
                        is UnaryDescriptorModel -> writeUnaryClientOverride(writer, serviceName, d)
                        is StreamingDescriptorModel -> writeStreamingClientOverride(writer, serviceName, d)
                        is BidirectionalDescriptorModel -> writeBidirectionalClientOverride(writer, serviceName, d)
                    }
                }
                writer.appendLine("}")
                writer.appendLine()

                // Inline reified so the user can write `urpc.create<MyService>()` and
                // `install(MyServiceImpl())` without passing the KClass explicitly. The
                // `T : $serviceFqn` upper bound makes overload resolution dispatch to
                // the right generated extension when multiple services are imported.
                writer.appendLine("inline fun <reified T : $serviceFqn> UrpcClientFactory.create(): T {")
                writer.appendLine("    @Suppress(\"UNCHECKED_CAST\")")
                writer.appendLine("    return ${serviceName}UrpcClient(this) as T")
                writer.appendLine("}")
                writer.appendLine()

                writer.appendLine("inline fun <reified T : $serviceFqn> UrpcRoute.install(impl: T) {")
                for (d in descriptors) {
                    when (d) {
                        is UnaryDescriptorModel -> writeUnaryInstall(writer, serviceName, d)
                        is StreamingDescriptorModel -> writeStreamingInstall(writer, serviceName, d)
                        is BidirectionalDescriptorModel -> writeBidirectionalInstall(writer, serviceName, d)
                    }
                }
                writer.appendLine("}")
            }
        }
    }

    // ----- descriptor blocks -----

    private fun writeUnaryDescriptor(w: OutputStreamWriter, d: UnaryDescriptorModel) {
        val reqRender = d.requestTypeRef?.render() ?: "Unit"
        val resRender = d.responseTypeRef.render()
        val reqSerializer = if (d.requestTypeRef != null) "serializer<$reqRender>()" else "serializer<Unit>()"
        w.appendLine("    val ${d.functionName}: ServiceDescriptor<$reqRender, $resRender> = ServiceDescriptor(")
        w.appendLine("        name = ${quote(d.wireName)},")
        w.appendLine("        requestSerializer = $reqSerializer,")
        w.appendLine("        responseSerializer = serializer<$resRender>(),")
        w.appendLine("        isUnitRequest = ${d.requestTypeRef == null},")
        w.appendLine("        isUnitResponse = ${d.responseTypeRef.isUnit},")
        w.appendLine("    )")
    }

    private fun writeStreamingDescriptor(w: OutputStreamWriter, d: StreamingDescriptorModel) {
        val reqRender = d.requestTypeRef?.render() ?: "Unit"
        val resRender = d.responseTypeRef.render()
        val reqSerializer = if (d.requestTypeRef != null) "serializer<$reqRender>()" else "serializer<Unit>()"
        w.appendLine("    val ${d.functionName}: StreamingServiceDescriptor<$reqRender, $resRender> = StreamingServiceDescriptor(")
        w.appendLine("        name = ${quote(d.wireName)},")
        w.appendLine("        requestSerializer = $reqSerializer,")
        w.appendLine("        responseSerializer = serializer<$resRender>(),")
        w.appendLine("        isUnitRequest = ${d.requestTypeRef == null},")
        w.appendLine("    )")
    }

    private fun writeBidirectionalDescriptor(w: OutputStreamWriter, d: BidirectionalDescriptorModel) {
        val reqRender = d.requestTypeRef.render()
        val resRender = d.responseTypeRef.render()
        w.appendLine("    val ${d.functionName}: BidirectionalServiceDescriptor<$reqRender, $resRender> = BidirectionalServiceDescriptor(")
        w.appendLine("        name = ${quote(d.wireName)},")
        w.appendLine("        requestSerializer = serializer<$reqRender>(),")
        w.appendLine("        responseSerializer = serializer<$resRender>(),")
        w.appendLine("    )")
    }

    // ----- generated client class overrides -----

    private fun writeUnaryClientOverride(w: OutputStreamWriter, serviceName: String, d: UnaryDescriptorModel) {
        val reqRender = d.requestTypeRef?.render() ?: "Unit"
        val resRender = d.responseTypeRef.render()
        val paramList = if (d.requestTypeRef != null) "request: $reqRender" else ""
        val callArg = if (d.requestTypeRef != null) "request" else "Unit"
        w.appendLine("    override suspend fun ${d.functionName}($paramList): $resRender =")
        w.appendLine("        urpc.callUnary(${serviceName}Descriptors.${d.functionName}, $callArg)")
    }

    private fun writeStreamingClientOverride(w: OutputStreamWriter, serviceName: String, d: StreamingDescriptorModel) {
        val reqRender = d.requestTypeRef?.render() ?: "Unit"
        val resRender = d.responseTypeRef.render()
        val paramList = if (d.requestTypeRef != null) "request: $reqRender" else ""
        val callArg = if (d.requestTypeRef != null) "request" else "Unit"
        w.appendLine("    override fun ${d.functionName}($paramList): Flow<$resRender> =")
        w.appendLine("        urpc.callStreaming(${serviceName}Descriptors.${d.functionName}, $callArg)")
    }

    private fun writeBidirectionalClientOverride(w: OutputStreamWriter, serviceName: String, d: BidirectionalDescriptorModel) {
        val reqRender = d.requestTypeRef.render()
        val resRender = d.responseTypeRef.render()
        w.appendLine("    override fun ${d.functionName}(requests: Flow<$reqRender>): Flow<$resRender> =")
        w.appendLine("        urpc.callBidirectional(${serviceName}Descriptors.${d.functionName}, requests)")
    }

    // ----- generated install lines -----

    private fun writeUnaryInstall(w: OutputStreamWriter, serviceName: String, d: UnaryDescriptorModel) {
        val handlerArg = if (d.requestTypeRef != null) "impl::${d.functionName}" else "{ impl.${d.functionName}() }"
        w.appendLine("    installUnary(")
        w.appendLine("        descriptor = ${serviceName}Descriptors.${d.functionName},")
        w.appendLine("        handler = $handlerArg,")
        w.appendLine("    )")
    }

    private fun writeStreamingInstall(w: OutputStreamWriter, serviceName: String, d: StreamingDescriptorModel) {
        val handlerArg = if (d.requestTypeRef != null) "impl::${d.functionName}" else "{ impl.${d.functionName}() }"
        w.appendLine("    installStreaming(")
        w.appendLine("        descriptor = ${serviceName}Descriptors.${d.functionName},")
        w.appendLine("        handler = $handlerArg,")
        w.appendLine("    )")
    }

    private fun writeBidirectionalInstall(w: OutputStreamWriter, serviceName: String, d: BidirectionalDescriptorModel) {
        w.appendLine("    installBidirectional(")
        w.appendLine("        descriptor = ${serviceName}Descriptors.${d.functionName},")
        w.appendLine("        handler = impl::${d.functionName},")
        w.appendLine("    )")
    }
}

private val OBJECT_METHOD_NAMES = setOf("equals", "hashCode", "toString")

private fun KSClassDeclaration.urpcServiceName(): String? {
    val ann = annotations.firstOrNull { it.shortName.asString() == "UrpcService" } ?: return null
    val raw = ann.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String
    return raw?.takeIf { it.isNotEmpty() }
}

private fun KSFunctionDeclaration.urpcWireNameOverride(): String? {
    val ann = annotations.firstOrNull { it.shortName.asString() == "UrpcWireName" } ?: return null
    return ann.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String
}

private fun KSType.isFlow(): Boolean =
    declaration.qualifiedName?.asString() == FLOW_FQN

private fun KSType.flowElement(): KSType? =
    if (isFlow()) arguments.firstOrNull()?.type?.resolve() else null

private fun quote(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// ----- model -----

private sealed class DescriptorModel {
    abstract val functionName: String
    abstract val wireName: String
}

private data class UnaryDescriptorModel(
    override val functionName: String,
    override val wireName: String,
    val requestTypeRef: TypeReference?,
    val responseTypeRef: TypeReference,
) : DescriptorModel()

private data class StreamingDescriptorModel(
    override val functionName: String,
    override val wireName: String,
    val requestTypeRef: TypeReference?,
    val responseTypeRef: TypeReference,
) : DescriptorModel()

private data class BidirectionalDescriptorModel(
    override val functionName: String,
    override val wireName: String,
    val requestTypeRef: TypeReference,
    val responseTypeRef: TypeReference,
) : DescriptorModel()

private data class TypeReference(
    val rendered: String,
    val isUnit: Boolean,
) {
    fun render(): String = rendered
}

private fun KSType.toTypeReference(): TypeReference {
    val decl = declaration
    val name = decl.qualifiedName?.asString() ?: decl.simpleName.asString()
    val typeArgs = arguments.mapNotNull { it.type?.resolve()?.toTypeReference() }
    val rendered = if (typeArgs.isEmpty()) {
        name + (if (isMarkedNullable) "?" else "")
    } else {
        name + typeArgs.joinToString(", ", "<", ">") { it.render() } + (if (isMarkedNullable) "?" else "")
    }
    return TypeReference(rendered = rendered, isUnit = name == "kotlin.Unit")
}
