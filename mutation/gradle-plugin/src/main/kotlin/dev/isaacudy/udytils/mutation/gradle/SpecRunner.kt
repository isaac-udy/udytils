package dev.isaacudy.udytils.mutation.gradle

import org.gradle.api.logging.Logging
import java.io.File

/**
 * Runs mutants for one JVM test task. Keeps one driver JVM between mutants, restarts the driver
 * after a hang, and runs each mutant in a once-per-process initialiser in a fresh driver JVM
 * started with that mutant active.
 */
internal class SpecRunner(
    private val spec: TestSpec,
    private val driverClasspath: List<File>,
    private val excludedEngines: Set<String>,
    val workDirectory: File,
) : AutoCloseable {
    private val logger = Logging.getLogger(SpecRunner::class.java)
    private var driver: Driver? = null
    private var warmed = true

    /** Tests a restarted driver runs with no mutant active before the next mutant, so the next mutant's timeouts apply to a warm JVM. */
    var warmupTests: List<Pair<Long, String>> = emptyList()

    /** Added to the first test's timeout in a JVM started for one mutant: the baseline duration of the first test of a cold JVM. */
    var coldStartMillis: Long = 0

    private fun driver(): Driver {
        val current = driver ?: Driver(spec, driverClasspath, workDirectory).also { driver = it }
        if (!warmed) {
            warmed = true
            val warmup = runCatching { current.run(0, warmupTests.map { (timeout, uid) -> timeout + coldStartMillis to uid }) }.getOrNull()
            if (warmup?.restart == true) {
                current.kill()
                return Driver(spec, driverClasspath, workDirectory).also { driver = it }
            }
        }
        return current
    }

    fun baseline(tags: Set<String>): Map<String, TestCase> {
        val tests = driver().discover(spec.testClassesDirs, excludedEngines).associateBy { it.uid }
        val selected = if (tags.isEmpty()) tests else tests.filterValues { t -> t.tags.any { it in tags } }
        driver().baseline(selected, tags, BASELINE_TIMEOUT_MILLIS)
        return selected
    }

    fun run(site: Site, tests: List<Pair<Long, String>>): RunReply {
        if (site.initScope) {
            Driver(spec, driverClasspath, File(workDirectory, "fresh"), activeMutant = site.id).use { fresh ->
                val cold = tests.mapIndexed { i, (timeout, uid) -> (if (i == 0) timeout + coldStartMillis else timeout) to uid }
                return attempt(fresh, site, cold) { fresh.kill() }
            }
        }
        return attempt(driver(), site, tests) { restart() }
    }

    private fun attempt(target: Driver, site: Site, tests: List<Pair<Long, String>>, onHang: () -> Unit): RunReply =
        try {
            target.run(site.id, tests).also { if (it.restart) onHang() }
        } catch (e: DriverTimeout) {
            logger.lifecycle("Mutant ${site.hexId} (${site.path}:${site.line}) hung the driver; restarting it.")
            onHang()
            RunReply(Status.TimedOut, null, tests.sumOf { it.first }, "the driver JVM stopped responding")
        }

    /**
     * Runs [tests] with no mutant active. A failure means an earlier mutant left state behind: the
     * driver restarts and the check returns false.
     */
    fun sanityCheck(tests: List<Pair<Long, String>>): Boolean {
        if (tests.isEmpty()) return true
        val verdict = try {
            driver().run(0, tests).also { if (it.restart) restart() }.status
        } catch (e: DriverTimeout) {
            Status.TimedOut
        }
        if (verdict != Status.Survived) {
            logger.lifecycle("${spec.projectPath}:${spec.testTask}: a test failed with no mutant active; restarting the driver.")
            restart()
            return false
        }
        return true
    }

    private fun restart() {
        driver?.kill()
        driver = null
        warmed = false
    }

    override fun close() {
        driver?.close()
        driver = null
    }

    private companion object {
        const val BASELINE_TIMEOUT_MILLIS = 3_600_000L
    }
}
