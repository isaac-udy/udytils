package dev.isaacudy.udytils.atlas

import java.io.File
import java.time.Instant

fun assemble(
    config: ResolvedAtlasConfig,
    rawNodes: List<RawNode>,
    rawEdges: List<RawEdge>,
    goldens: List<GoldenFile>,
): AtlasManifest {
    val qualifiedBySimple = rawNodes.groupBy { it.destinationName }
    val withResultQNames = rawNodes.filter { it.isWithResult }.map { it.qualifiedName }.toSet()

    val duplicateScreenNames = rawNodes.groupBy { it.screenName }.filterValues { it.size > 1 }.keys

    fun displayName(node: RawNode): String {
        if (node.screenName !in duplicateScreenNames) return node.screenName
        val qualifier = node.packageName.split(".").lastOrNull { it != "ui" && it != "client" } ?: node.moduleLabel
        return "${node.screenName} ($qualifier)"
    }

    val goldensByQName = assignGoldensToNodes(rawNodes, goldens)

    val nodes = rawNodes.map { raw ->
        val variants = goldensByQName[raw.qualifiedName] ?: emptyList()
        val sortedVariants = variants.sortedBy { it.label }
        val defaultIdx = pickDefaultVariant(sortedVariants)
        AtlasNode(
            qualifiedName = raw.qualifiedName,
            destinationName = raw.destinationName,
            screenName = raw.screenName,
            displayName = displayName(raw),
            featureGroup = raw.featureGroup,
            moduleLabel = raw.moduleLabel,
            sourceFile = raw.sourceFile,
            packageName = raw.packageName,
            isWithResult = raw.isWithResult,
            navigationPath = raw.navigationPath,
            isShellActive = raw.isShellActive,
            variants = sortedVariants,
            defaultVariantIndex = defaultIdx,
        )
    }

    val resolvedEdges = mutableSetOf<Triple<String, String, EdgeKind>>()
    val unresolvedEdges = mutableListOf<UnresolvedEdge>()

    for (edge in rawEdges) {
        if (edge.resolved && edge.sourceScreen != null) {
            val sourceNode = rawNodes.find { it.screenName == edge.sourceScreen }
            if (sourceNode != null) {
                val targetNodes = qualifiedBySimple[edge.targetDestination]
                if (targetNodes != null && targetNodes.size == 1) {
                    val targetQName = targetNodes[0].qualifiedName
                    val kind = if (withResultQNames.contains(targetQName)) EdgeKind.RESULT else EdgeKind.OPEN
                    resolvedEdges.add(Triple(sourceNode.qualifiedName, targetQName, kind))
                } else if (targetNodes != null && targetNodes.size > 1) {
                    val samePackage = targetNodes.find { it.packageName == sourceNode.packageName }
                    val sameModule = targetNodes.find { it.moduleLabel == sourceNode.moduleLabel }
                    val best = samePackage ?: sameModule
                    if (best != null) {
                        val kind = if (withResultQNames.contains(best.qualifiedName)) EdgeKind.RESULT else EdgeKind.OPEN
                        resolvedEdges.add(Triple(sourceNode.qualifiedName, best.qualifiedName, kind))
                    } else {
                        unresolvedEdges.add(UnresolvedEdge(edge.sourceFile, edge.line, edge.text))
                    }
                } else {
                    unresolvedEdges.add(UnresolvedEdge(edge.sourceFile, edge.line, edge.text))
                }
            } else {
                unresolvedEdges.add(UnresolvedEdge(edge.sourceFile, edge.line, edge.text))
            }
        } else if (!edge.resolved) {
            unresolvedEdges.add(UnresolvedEdge(edge.sourceFile, edge.line, edge.text))
        } else if (edge.sourceScreen == null) {
            unresolvedEdges.add(UnresolvedEdge(edge.sourceFile, edge.line, edge.text))
        }
    }

    for (chrome in config.chromeEdges) {
        val srcNodes = qualifiedBySimple[chrome.sourceDestination]
        val tgtNodes = qualifiedBySimple[chrome.targetDestination]
        if (srcNodes?.size == 1 && tgtNodes?.size == 1) {
            resolvedEdges.add(Triple(srcNodes[0].qualifiedName, tgtNodes[0].qualifiedName, EdgeKind.CHROME))
        }
    }

    val matchedPaths = goldensByQName.values.flatMap { vs -> vs.map { it.imagePath } }.toSet()
    val destPackages = rawNodes.map { it.packageName }.toSet()

    val unmatchedByPkg = mutableMapOf<String, MutableList<GoldenFile>>()
    for (golden in goldens) {
        if (golden.relativePath in matchedPaths) continue
        val pkg = golden.packagePath.replace('/', '.')
        unmatchedByPkg.getOrPut(pkg) { mutableListOf() }.add(golden)
    }

    val syntheticNodes = mutableListOf<AtlasNode>()
    for ((pkg, pkgGoldens) in unmatchedByPkg) {
        if (destPackages.contains(pkg)) continue
        val lastSeg = pkg.split(".").last()
        val displayName = lastSeg.replaceFirstChar { it.uppercase() }
        val variants = pkgGoldens.map { g ->
            AtlasVariant(
                label = g.fileName.removeSuffix("Preview").ifEmpty { g.fileName },
                imagePath = g.relativePath,
                width = g.width,
                height = g.height,
            )
        }.sortedBy { it.label }
        syntheticNodes.add(AtlasNode(
            qualifiedName = "synthetic.$pkg",
            destinationName = lastSeg,
            screenName = displayName,
            displayName = displayName,
            featureGroup = pkg.split(".").take(2).joinToString("."),
            moduleLabel = "standalone",
            sourceFile = "",
            packageName = pkg,
            isWithResult = false,
            navigationPath = null,
            isShellActive = false,
            variants = variants,
            defaultVariantIndex = pickDefaultVariant(variants),
            synthetic = true,
        ))
    }

    val trueOrphanCount = unmatchedByPkg.entries
        .filter { destPackages.contains(it.key) }
        .sumOf { it.value.size }

    return AtlasManifest(
        projectName = config.projectName,
        generatedAt = Instant.now().toString(),
        nodes = nodes + syntheticNodes,
        edges = resolvedEdges.map { (src, tgt, kind) -> AtlasEdge(src, tgt, kind) },
        unresolvedEdges = unresolvedEdges,
        unmatchedGoldens = trueOrphanCount,
        totalGoldens = goldens.size,
    )
}

internal fun assignGoldensToNodes(
    nodes: List<RawNode>,
    goldens: List<GoldenFile>,
): Map<String, List<AtlasVariant>> {
    val screenNames = nodes.map { it.screenName }.distinct().sortedByDescending { it.length }
    val result = mutableMapOf<String, MutableList<AtlasVariant>>()
    val matched = mutableSetOf<String>()

    for (golden in goldens) {
        val name = golden.fileName
        val matchingScreen = screenNames.firstOrNull { screen ->
            name.startsWith("${screen}Screen") || name.startsWith(screen)
        } ?: continue

        val candidates = nodes.filter { it.screenName == matchingScreen }
        val node = candidates.find { goldenPackageMatchesNode(golden.packagePath, it.packageName) }
        if (node == null) continue

        val variantLabel = deriveVariantLabel(name, matchingScreen)
        result.getOrPut(node.qualifiedName) { mutableListOf() }.add(
            AtlasVariant(label = variantLabel, imagePath = golden.relativePath, width = golden.width, height = golden.height)
        )
        matched.add(golden.relativePath)
    }

    val nodesByPkg = nodes.groupBy { it.packageName }
    for (golden in goldens) {
        if (golden.relativePath in matched) continue
        val goldenPkg = golden.packagePath.replace('/', '.')
        val pkgNodes = nodesByPkg[goldenPkg]
        if (pkgNodes == null || pkgNodes.size != 1) continue
        val node = pkgNodes[0]
        val label = golden.fileName.removeSuffix("Preview").ifEmpty { golden.fileName }
        result.getOrPut(node.qualifiedName) { mutableListOf() }.add(
            AtlasVariant(label = label, imagePath = golden.relativePath, width = golden.width, height = golden.height)
        )
        matched.add(golden.relativePath)
    }

    return result
}

private fun goldenPackageMatchesNode(goldenPkgPath: String, nodePackage: String): Boolean {
    val nodePkgPath = nodePackage.replace('.', '/')
    val goldenSlash = goldenPkgPath.replace('.', '/')
    return goldenSlash.startsWith(nodePkgPath) || nodePkgPath.startsWith(goldenSlash)
}

private fun deriveVariantLabel(goldenName: String, screenName: String): String {
    var stripped = goldenName
    if (stripped.startsWith("${screenName}Screen")) {
        stripped = stripped.removePrefix("${screenName}Screen")
    } else {
        stripped = stripped.removePrefix(screenName)
    }
    stripped = stripped.removeSuffix("Preview")
    return stripped.ifEmpty { "Default" }
}

internal fun pickDefaultVariant(variants: List<AtlasVariant>): Int {
    if (variants.isEmpty()) return 0
    val defaultIdx = variants.indexOfFirst { it.label == "Default" }
    if (defaultIdx >= 0) return defaultIdx
    val loadedIdx = variants.indexOfFirst { it.label.contains("Loaded", ignoreCase = true) }
    if (loadedIdx >= 0) return loadedIdx
    return variants.indices.minByOrNull { variants[it].label.length } ?: 0
}

fun copyGoldensToOutput(config: ResolvedAtlasConfig, manifest: AtlasManifest) {
    val imagesDir = File(config.outputDir, "images")
    imagesDir.mkdirs()

    for (node in manifest.nodes) {
        for (variant in node.variants) {
            val src = File(config.repoRoot, variant.imagePath)
            if (!src.exists()) continue
            val dest = File(imagesDir, variant.imagePath)
            dest.parentFile.mkdirs()
            src.copyTo(dest, overwrite = true)
        }
    }
}
