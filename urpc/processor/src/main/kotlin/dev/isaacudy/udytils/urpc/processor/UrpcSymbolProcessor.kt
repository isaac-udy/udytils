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
private const val URPC_WIRE_NAME_FQN = "dev.isaacudy.udytils.urpc.UrpcWireName"

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
    ): UnaryDescriptorModel? {
        val fnName = fn.simpleName.asString()
        val isSuspend = Modifier.SUSPEND in fn.modifiers
        if (!isSuspend) {
            logger.error(
                "urpc only supports `suspend fun` functions in this iteration. " +
                        "Function `$fnName` on ${service.qualifiedName?.asString()} is not suspend " +
                        "(streaming / bidirectional KSP support is planned).",
                fn,
            )
            return null
        }

        if (fn.parameters.size > 1) {
            logger.error(
                "urpc functions take 0 or 1 parameter. `$fnName` has ${fn.parameters.size}.",
                fn,
            )
            return null
        }

        val requestType: KSType? = fn.parameters.firstOrNull()?.type?.resolve()
        val responseType: KSType = fn.returnType?.resolve()
            ?: error("Could not resolve return type of $fnName")

        val wireName = fn.urpcWireNameOverride()?.let { "$wirePrefix.$it" }
            ?: "$wirePrefix.$fnName"

        return UnaryDescriptorModel(
            functionName = fnName,
            wireName = wireName,
            requestTypeRef = requestType?.toTypeReference(),
            responseTypeRef = responseType.toTypeReference(),
        )
    }

    private fun writeGeneratedFile(
        service: KSClassDeclaration,
        descriptors: List<UnaryDescriptorModel>,
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
                writer.appendLine("@file:Suppress(\"UNUSED_PARAMETER\", \"RedundantSuppression\")")
                writer.appendLine()
                writer.appendLine("package $packageName")
                writer.appendLine()
                writer.appendLine("import dev.isaacudy.udytils.urpc.ServiceDescriptor")
                writer.appendLine("import dev.isaacudy.udytils.urpc.UrpcClientFactory")
                writer.appendLine("import dev.isaacudy.udytils.urpc.UrpcRoute")
                writer.appendLine("import kotlinx.serialization.serializer")
                writer.appendLine()

                writer.appendLine("@PublishedApi")
                writer.appendLine("internal object ${serviceName}Descriptors {")
                for (d in descriptors) {
                    val reqRender = d.requestTypeRef?.render() ?: "Unit"
                    val resRender = d.responseTypeRef.render()
                    val reqSerializer = if (d.requestTypeRef != null) {
                        "serializer<$reqRender>()"
                    } else {
                        "serializer<Unit>()"
                    }
                    writer.appendLine("    val ${d.functionName}: ServiceDescriptor<$reqRender, $resRender> = ServiceDescriptor(")
                    writer.appendLine("        name = ${quote(d.wireName)},")
                    writer.appendLine("        requestSerializer = $reqSerializer,")
                    writer.appendLine("        responseSerializer = serializer<$resRender>(),")
                    writer.appendLine("        isUnitRequest = ${d.requestTypeRef == null},")
                    writer.appendLine("        isUnitResponse = ${d.responseTypeRef.isUnit},")
                    writer.appendLine("    )")
                }
                writer.appendLine("}")
                writer.appendLine()

                writer.appendLine("@PublishedApi")
                writer.appendLine("internal class ${serviceName}UrpcClient(")
                writer.appendLine("    private val urpc: UrpcClientFactory,")
                writer.appendLine(") : $serviceFqn {")
                for (d in descriptors) {
                    val reqRender = d.requestTypeRef?.render() ?: "Unit"
                    val resRender = d.responseTypeRef.render()
                    val paramList = if (d.requestTypeRef != null) "request: $reqRender" else ""
                    val callArg = if (d.requestTypeRef != null) "request" else "Unit"
                    writer.appendLine("    override suspend fun ${d.functionName}($paramList): $resRender =")
                    writer.appendLine("        urpc.callUnary(${serviceName}Descriptors.${d.functionName}, $callArg)")
                }
                writer.appendLine("}")
                writer.appendLine()

                // Inline reified so the user can write `urpc.create<MyService>()` and
                // `install(MyServiceImpl())` without passing the KClass explicitly. The
                // `T : $serviceFqn` upper bound makes overload resolution dispatch to the
                // right generated extension when multiple services are imported.
                writer.appendLine("inline fun <reified T : $serviceFqn> UrpcClientFactory.create(): T {")
                writer.appendLine("    @Suppress(\"UNCHECKED_CAST\")")
                writer.appendLine("    return ${serviceName}UrpcClient(this) as T")
                writer.appendLine("}")
                writer.appendLine()

                writer.appendLine("inline fun <reified T : $serviceFqn> UrpcRoute.install(impl: T) {")
                for (d in descriptors) {
                    val handlerArg = if (d.requestTypeRef != null) "impl::${d.functionName}" else "{ impl.${d.functionName}() }"
                    writer.appendLine("    installUnary(")
                    writer.appendLine("        descriptor = ${serviceName}Descriptors.${d.functionName},")
                    writer.appendLine("        handler = $handlerArg,")
                    writer.appendLine("    )")
                }
                writer.appendLine("}")
            }
        }
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

private fun quote(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private data class UnaryDescriptorModel(
    val functionName: String,
    val wireName: String,
    val requestTypeRef: TypeReference?,
    val responseTypeRef: TypeReference,
)

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
