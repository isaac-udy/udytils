package dev.isaacudy.udytils.atlas

import java.io.File
import java.nio.ByteBuffer

fun scanNodes(config: ResolvedAtlasConfig): List<RawNode> {
    val destinationPattern = Regex("""@NavigationDestination\(\s*(\w+)::class\s*\)""")
    val withResultPattern = Regex(""":\s*NavigationKey\.WithResult<""")
    val navigationPathPattern = Regex("""@NavigationPath\(\s*"([^"]*?)"\s*\)""")
    val shellActivePattern = Regex("""shellActive\s*\(""")
    val shellEmptyPattern = Regex("""shellEmpty\s*\(\s*\)""")

    val nodes = mutableListOf<RawNode>()

    for (sourceRoot in config.sourceRoots) {
        val rootDir = File(config.repoRoot, sourceRoot)
        if (!rootDir.exists()) continue

        rootDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                for (match in destinationPattern.findAll(text)) {
                    val destName = match.groupValues[1]
                    if (destName.endsWith("Destination")) {
                        val relPath = file.relativeTo(config.repoRoot).path
                        val pkg = extractPackage(text)
                        val featureGroup = deriveFeatureGroup(pkg, relPath, config.featureGroupFallbacks)
                        val moduleLabel = deriveModuleLabel(relPath)
                        val screenName = deriveScreenName(destName)

                        val destFile = findDestinationFile(file.parentFile, destName)
                        val destText = destFile?.readText()

                        val isWithResult = destText?.let { withResultPattern.containsMatchIn(it) } ?: false
                        val navPath = destText?.let {
                            navigationPathPattern.find(it)?.groupValues?.get(1)
                        }
                        val hasShellActive = shellActivePattern.containsMatchIn(text)
                        val hasShellEmpty = shellEmptyPattern.containsMatchIn(text)

                        nodes.add(
                            RawNode(
                                qualifiedName = if (pkg.isNotEmpty()) "$pkg.$destName" else destName,
                                destinationName = destName,
                                screenName = screenName,
                                featureGroup = featureGroup,
                                moduleLabel = moduleLabel,
                                sourceFile = relPath,
                                packageName = pkg,
                                isWithResult = isWithResult,
                                navigationPath = navPath,
                                isShellActive = hasShellActive || hasShellEmpty,
                            )
                        )
                    }
                }
            }
    }
    return nodes
}

data class RawNode(
    val qualifiedName: String,
    val destinationName: String,
    val screenName: String,
    val featureGroup: String,
    val moduleLabel: String,
    val sourceFile: String,
    val packageName: String,
    val isWithResult: Boolean,
    val navigationPath: String?,
    val isShellActive: Boolean,
)

internal fun extractPackage(text: String): String {
    val match = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(text)
    return match?.groupValues?.get(1) ?: ""
}

internal fun deriveFeatureGroup(
    pkg: String,
    relPath: String,
    fallbacks: List<Pair<String, String>>,
): String {
    val parts = pkg.split(".")
    val featureIdx = parts.indexOf("feature")
    if (featureIdx >= 0 && featureIdx + 1 < parts.size) {
        return parts[featureIdx + 1]
    }
    for ((prefix, label) in fallbacks) {
        if (relPath.startsWith(prefix)) return label
    }
    return "other"
}

internal fun deriveModuleLabel(relPath: String): String {
    val srcIdx = relPath.indexOf("/src/")
    if (srcIdx < 0) return relPath
    return relPath.substring(0, srcIdx).replace('/', ':')
}

private fun deriveScreenName(destName: String): String {
    return destName.removeSuffix("Destination")
}

private fun findDestinationFile(dir: File, destName: String): File? {
    val candidate = File(dir, "$destName.kt")
    if (candidate.exists()) return candidate
    dir.listFiles()?.forEach { sub ->
        if (sub.isDirectory) {
            val nested = File(sub, "$destName.kt")
            if (nested.exists()) return nested
        }
    }
    return null
}

data class RawEdge(
    val sourceFile: String,
    val line: Int,
    val sourceScreen: String?,
    val targetDestination: String,
    val text: String,
    val resolved: Boolean,
)

fun scanEdges(config: ResolvedAtlasConfig, knownDestinations: Set<String>): List<RawEdge> {
    val openPattern = Regex("""\.open\(\s*(\w+)""")
    val multiLineOpenPattern = Regex("""\.open\(\s*\n\s*(\w+)""")
    val edges = mutableListOf<RawEdge>()

    fun isCommentLine(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
    }

    val receiverDenylist = setOf("window", "dialog", "popup", "channel", "stream", "file", "connection")
    fun isDeniedReceiver(text: String, matchStart: Int): Boolean {
        val before = text.substring(0, matchStart)
        val receiver = Regex("""(\w+)\s*$""").find(before)?.groupValues?.get(1) ?: return false
        return receiverDenylist.contains(receiver)
    }

    for (sourceRoot in config.sourceRoots) {
        val rootDir = File(config.repoRoot, sourceRoot)
        if (!rootDir.exists()) continue

        rootDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val relPath = file.relativeTo(config.repoRoot).path
                val text = file.readText()
                val lines = text.lines()
                val sourceScreen = inferSourceScreen(file)

                for (match in openPattern.findAll(text)) {
                    val targetToken = match.groupValues[1]
                    val lineNum = text.substring(0, match.range.first).count { it == '\n' } + 1
                    val lineText = lines.getOrElse(lineNum - 1) { "" }
                    if (isCommentLine(lineText)) continue
                    if (isDeniedReceiver(text, match.range.first)) continue
                    val isKnown = knownDestinations.contains(targetToken)

                    edges.add(
                        RawEdge(
                            sourceFile = relPath,
                            line = lineNum,
                            sourceScreen = sourceScreen,
                            targetDestination = targetToken,
                            text = lineText.trim(),
                            resolved = isKnown,
                        )
                    )
                }

                for (match in multiLineOpenPattern.findAll(text)) {
                    val targetToken = match.groupValues[1]
                    val alreadyCaptured = edges.any { it.sourceFile == relPath && it.targetDestination == targetToken &&
                        kotlin.math.abs(it.line - (text.substring(0, match.range.first).count { it == '\n' } + 1)) <= 1 }
                    if (alreadyCaptured) continue

                    val lineNum = text.substring(0, match.range.first).count { it == '\n' } + 1
                    val lineText = lines.getOrElse(lineNum - 1) { "" }
                    if (isCommentLine(lineText)) continue
                    if (isDeniedReceiver(text, match.range.first)) continue
                    val isKnown = knownDestinations.contains(targetToken)

                    edges.add(
                        RawEdge(
                            sourceFile = relPath,
                            line = lineNum,
                            sourceScreen = sourceScreen,
                            targetDestination = targetToken,
                            text = lineText.trim(),
                            resolved = isKnown,
                        )
                    )
                }
            }
    }
    return edges
}

private fun inferSourceScreen(file: File): String? {
    val name = file.nameWithoutExtension
    val screenName = when {
        name.endsWith("ViewModel") -> name.removeSuffix("ViewModel")
        name.endsWith("Screen") -> name.removeSuffix("Screen")
        else -> null
    }
    return screenName
}

data class GoldenFile(
    val relativePath: String,
    val fileName: String,
    val packagePath: String,
    val width: Int,
    val height: Int,
    val moduleLabel: String,
)

fun scanGoldens(config: ResolvedAtlasConfig): List<GoldenFile> {
    val goldens = mutableListOf<GoldenFile>()
    for (root in config.goldenRoots) {
        val dir = File(config.repoRoot, root.path)
        if (!dir.exists()) continue
        dir.walkTopDown()
            .filter { it.extension == "png" }
            .forEach { file ->
                val relToRoot = file.relativeTo(config.repoRoot).path
                val relToGoldenRoot = file.relativeTo(dir).parent ?: ""
                val dims = readPngDimensions(file)
                goldens.add(
                    GoldenFile(
                        relativePath = relToRoot,
                        fileName = file.nameWithoutExtension,
                        packagePath = relToGoldenRoot.replace(File.separatorChar, '.'),
                        width = dims.first,
                        height = dims.second,
                        moduleLabel = root.moduleLabel,
                    )
                )
            }
    }
    return goldens
}

internal fun readPngDimensions(file: File): Pair<Int, Int> {
    try {
        val bytes = file.inputStream().use { it.readNBytes(24) }
        if (bytes.size < 24) return 800 to 800
        val buf = ByteBuffer.wrap(bytes)
        buf.position(16)
        val width = buf.int
        val height = buf.int
        return width to height
    } catch (_: java.io.IOException) {
        return 800 to 800
    }
}
