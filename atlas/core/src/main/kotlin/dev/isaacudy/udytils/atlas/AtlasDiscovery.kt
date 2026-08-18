package dev.isaacudy.udytils.atlas

import java.io.File

object AtlasDiscovery {

    fun discoverProjectName(repoRoot: File): String {
        val settings = File(repoRoot, "settings.gradle.kts")
        if (settings.exists()) {
            val match = Regex("""rootProject\.name\s*=\s*"([^"]+)"""").find(settings.readText())
            if (match != null) return match.groupValues[1]
        }
        val settingsGroovy = File(repoRoot, "settings.gradle")
        if (settingsGroovy.exists()) {
            val match = Regex("""rootProject\.name\s*=\s*['"]([^'"]+)['"]""").find(settingsGroovy.readText())
            if (match != null) return match.groupValues[1]
        }
        return repoRoot.name
    }

    fun discoverSourceRoots(repoRoot: File): List<String> =
        walk(repoRoot, pruned(repoRoot)) { it.name == "kotlin" && isMainSourceSet(it) }

    fun discoverGoldenRoots(repoRoot: File): List<GoldenRoot> =
        walk(repoRoot, pruned(repoRoot)) { it.name == "images" && isSnapshotDir(it) }
            .map { GoldenRoot(path = it, moduleLabel = deriveModuleLabel(it)) }

    private fun isMainSourceSet(dir: File): Boolean {
        val srcParent = dir.parentFile ?: return false
        return srcParent.name.endsWith("Main") && srcParent.parentFile?.name == "src"
    }

    private fun isSnapshotDir(dir: File): Boolean {
        val parent = dir.parentFile ?: return false
        return parent.name == "snapshots" && parent.parentFile?.let { it.name == "src" || it.parentFile?.name == "src" } == true
    }

    private fun deriveModuleLabel(goldenRelPath: String): String {
        val srcIdx = goldenRelPath.indexOf("/src/")
        if (srcIdx < 0) return goldenRelPath
        return goldenRelPath.substring(0, srcIdx).replace('/', ':')
    }

    internal fun pruned(repoRoot: File): Set<String> {
        val submodules = mutableSetOf<String>()
        val gitmodules = File(repoRoot, ".gitmodules")
        if (gitmodules.exists()) {
            Regex("""path\s*=\s*(.+)""").findAll(gitmodules.readText()).forEach {
                submodules.add(it.groupValues[1].trim())
            }
        }
        return submodules
    }

    internal fun walk(repoRoot: File, submodulePaths: Set<String>, accept: (File) -> Boolean): List<String> {
        val results = mutableListOf<String>()
        fun recurse(dir: File, relPath: String) {
            if (dir.name.startsWith(".")) return
            if (dir.name == "build" || dir.name == "node_modules") return
            if (submodulePaths.contains(relPath)) return
            if (accept(dir)) {
                results.add(relPath)
                return
            }
            dir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { child ->
                val childRel = if (relPath.isEmpty()) child.name else "$relPath/${child.name}"
                recurse(child, childRel)
            }
        }
        recurse(repoRoot, "")
        return results
    }
}
