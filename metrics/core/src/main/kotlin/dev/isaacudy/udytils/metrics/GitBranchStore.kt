package dev.isaacudy.udytils.metrics

import java.io.File

/**
 * Stores one JSON file per [MetricsRun] on a dedicated git branch, without ever touching the
 * working tree or index: runs are appended with git plumbing (`hash-object` → `mktree` →
 * `commit-tree` → `update-ref`). The branch never needs to be checked out; CI pushes it like any
 * other branch.
 */
class GitBranchStore(
    private val repoRoot: File,
    private val branch: String = "metrics",
) {
    private val ref = "refs/heads/$branch"

    /** Appends [run] as `run-<timestamp>-<commit>.json`. Replaces an existing entry of the same name. */
    fun append(run: MetricsRun) {
        val name = "run-${run.timestamp.replace(":", "").replace("-", "")}-${run.commit.take(9)}.json"
        val json = metricsJson.encodeToString(MetricsRun.serializer(), run)
        val blob = git("hash-object", "-w", "--stdin", stdin = json)
        val entries = existingEntries().filterKeys { it != name } + (name to blob)
        val tree = git(
            "mktree",
            stdin = entries.entries.joinToString("\n") { (file, hash) -> "100644 blob $hash\t$file" },
        )
        val parent = headOrNull()
        val commitArgs = listOfNotNull(
            "commit-tree", tree,
            parent?.let { "-p" }, parent,
            "-m", "metrics: ${run.commit.take(9)} on ${run.branch}",
        )
        val commit = git(*commitArgs.toTypedArray())
        git("update-ref", ref, commit)
    }

    /** Every stored run, oldest first (by file name, which starts with the timestamp). */
    fun readRuns(): List<MetricsRun> {
        headOrNull() ?: return emptyList()
        return existingEntries().keys.sorted().map { name ->
            metricsJson.decodeFromString(MetricsRun.serializer(), git("show", "$ref:$name"))
        }
    }

    private fun existingEntries(): Map<String, String> {
        headOrNull() ?: return emptyMap()
        return git("ls-tree", ref).lines().filter { it.isNotBlank() }.associate { line ->
            // "100644 blob <hash>\t<name>"
            val (meta, name) = line.split('\t', limit = 2)
            name to meta.split(' ')[2]
        }
    }

    private fun headOrNull(): String? = runCatching { git("rev-parse", "--verify", ref) }.getOrNull()

    private fun git(vararg args: String, stdin: String? = null): String {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(repoRoot)
            .redirectErrorStream(false)
            .start()
        if (stdin != null) process.outputStream.bufferedWriter().use { it.write(stdin) }
        else process.outputStream.close()
        val out = process.inputStream.bufferedReader().readText().trim()
        val err = process.errorStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $err" }
        return out
    }
}
