package dev.isaacudy.udytils.mutation.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.security.MessageDigest

/**
 * Runs each selected mutant against the JVM tests that execute it.
 *
 * 1. Reads the compiler-plugin manifests and joins each site to its Constructs.
 * 2. Keeps the sites that a catalog rule selects, that no `ignore` or `excludeCalls` entry names,
 *    whose Constructs expect tests, and that the operator's sample includes.
 * 3. Per JVM test task: discovers the tests, runs a baseline with hit recording, then runs each
 *    mutant against the passing tests that hit the mutant and carry an expected category tag,
 *    fastest first, stopping at the first failure.
 * 4. Writes `results.json` and `summary.txt` to [outputDirectory].
 */
@DisableCachingByDefault(because = "Runs tests; the result depends on the test JVMs, not only on the declared inputs.")
abstract class RunMutantsTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.ABSOLUTE) abstract val manifestDirectories: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.ABSOLUTE) abstract val testSpecs: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val driverClasspath: ConfigurableFileCollection
    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE) abstract val classification: RegularFileProperty
    @get:Input abstract val moduleExpectations: MapProperty<String, String>
    @get:Input abstract val modules: MapProperty<String, String>
    @get:Input abstract val rules: ListProperty<String>
    @get:Input abstract val ignored: MapProperty<String, String>
    @get:Input abstract val compiledOperators: SetProperty<String>
    @get:Input @get:Optional abstract val runOperators: ListProperty<String>
    @get:Input abstract val excludedCalls: SetProperty<String>
    @get:Input abstract val samples: MapProperty<String, Double>
    @get:Input @get:Optional abstract val sampleSeed: Property<Long>
    @get:Input abstract val excludedCategories: SetProperty<String>
    @get:Input abstract val excludedEngines: SetProperty<String>
    @get:Input abstract val hosts: ListProperty<String>
    @get:Input abstract val testTimeoutFactor: Property<Double>
    @get:Input abstract val testTimeoutConstantMillis: Property<Long>
    @get:Input @get:Optional abstract val changedSince: Property<String>
    @get:Input @get:Optional abstract val onlyModules: ListProperty<String>
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:Internal abstract val workDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun runMutants() {
        val started = System.currentTimeMillis()
        val repository = repositoryDirectory.get().asFile
        val work = workDirectory.get().asFile.apply { mkdirs() }
        val ruleSpecs = rules.get().map(RuleSpec::decode)
        require(ruleSpecs.isNotEmpty()) { "runMutants requires at least one rule in the mutation { } block." }
        Operators.checkSelection(compiledOperators.get(), ruleSpecs, runOperators.getOrElse(emptyList()), samples.get())
        val seed = resolveSampleSeed(work)
        val selection = select(repository, ruleSpecs, seed)

        val specs = testSpecs.files.filter { it.isFile }.sortedBy { it.path }
            .map { reportJson.decodeFromString(TestSpec.serializer(), it.readText()) }
        val fingerprint = fingerprint(specs, seed)
        val journal = File(work, "results.jsonl")
        val previous = readJournal(journal, fingerprint)
        if (previous.isEmpty()) journal.delete()

        val results = LinkedHashMap<Long, MutantResult>()
        val baselineFailures = mutableListOf<BaselineFailure>()
        val pending = selection.selected.associateBy { it.id }.toMutableMap()
        val excluded = excludedCategories.get()
        previous.forEach { (id, result) -> if (pending.remove(id) != null) results[id] = result }

        for (spec in specs) {
            if (pending.isEmpty()) break
            if (spec.testClassesDirs.none { dir -> File(dir).walkTopDown().any { it.isFile && it.extension == "class" } }) continue
            val runner = SpecRunner(spec, driverClasspath.files.toList(), excludedEngines.get(), File(work, "drivers/${spec.projectPath.trim(':').replace(':', '-')}-${spec.testTask}"))
            runner.use {
                val tags = selection.selected.flatMap { (it.expectation as? Expectation.AnyOf)?.categories.orEmpty() }.toSet()
                val baselineTags = if (selection.selected.any { it.expectation == Expectation.Unclassified }) emptySet() else tags
                val tests = runner.baseline(baselineTags)
                tests.values.filter { !it.passed }.forEach { baselineFailures += BaselineFailure(spec.projectPath, it.uid) }
                File(runner.workDirectory, "baseline.tsv").writeText(
                    tests.values.joinToString("\n") { "${it.millis}\t${if (it.passed) "passed" else "failed"}\t${it.hits.size}\t${describe(it)}" },
                )
                logger.lifecycle("${spec.projectPath}:${spec.testTask}: baseline ran ${tests.size} tests, ${tests.values.count { !it.passed }} failed.")
                val sanityTests = tests.values.filter { it.passed }.sortedBy { it.millis }.take(SANITY_TESTS).map { timeoutFor(it) to it.uid }
                runner.warmupTests = sanityTests
                runner.coldStartMillis = tests.values.maxOfOrNull { it.millis } ?: 0
                val queue = ArrayDeque(pending.values.toList())
                val sinceSanity = mutableListOf<Site>()
                var reruns = MAX_RERUNS
                while (queue.isNotEmpty()) {
                    val site = queue.removeFirst()
                    val candidates = tests.values
                        .filter { it.passed && site.id in it.hits && site.expectation.accepts(it.tags) && it.tags.none { tag -> tag in excluded } }
                        .sortedBy { it.millis }
                    if (candidates.isEmpty()) continue
                    val reply = runner.run(site, candidates.map { timeoutFor(it) to it.uid })
                    val (verdict, failing, millis) = Triple(reply.status, reply.failingTest, reply.millis)
                    val earlier = results[site.id]
                    val result = site.result(verdict, selection, fingerprint).copy(
                        killedBy = failing?.let { uid -> tests[uid]?.let { describe(it) } ?: uid },
                        failure = reply.failure,
                        candidateTests = (earlier?.candidateTests ?: 0) + candidates.size,
                        testsRun = (earlier?.testsRun ?: 0) +
                            if (failing == null) candidates.size else candidates.indexOfFirst { it.uid == failing } + 1,
                        millis = (earlier?.millis ?: 0) + millis,
                    )
                    results[site.id] = result
                    if (verdict != Status.Survived) pending.remove(site.id)
                    journal.appendText(lineJson.encodeToString(MutantResult.serializer(), result) + "\n")
                    sinceSanity += site
                    if (sinceSanity.size >= SANITY_INTERVAL || queue.isEmpty()) {
                        if (!runner.sanityCheck(sanityTests) && reruns-- > 0) {
                            logger.lifecycle("${spec.projectPath}:${spec.testTask}: running the last ${sinceSanity.size} mutants again.")
                            sinceSanity.forEach { results.remove(it.id); pending[it.id] = it }
                            sinceSanity.forEach { queue.addFirst(it) }
                        }
                        sinceSanity.clear()
                    }
                }
            }
        }
        selection.selected.filter { it.id !in results }.forEach { results[it.id] = it.result(Status.Uncovered, selection, fingerprint) }

        val ordered = results.values.sortedWith(compareBy({ it.path }, { it.line }, { it.id }))
        val killed = ordered.count { it.status == Status.Killed.label }
        val timedOut = ordered.count { it.status == Status.TimedOut.label }
        val survived = ordered.count { it.status == Status.Survived.label }
        val covered = killed + timedOut + survived
        val report = Report(
            hosts = specs.map { if (it.testTask == "testAndroidHostTest") "android" else "jvm" }.distinct().sorted(),
            summary = Summary(
                mutants = ordered.size,
                killed = killed,
                timedOut = timedOut,
                survived = survived,
                uncovered = ordered.count { it.status == Status.Uncovered.label },
                notExpected = selection.notExpected,
                ignored = selection.ignored,
                notSelected = selection.notSelected,
                excludedCalls = selection.excludedCalls,
                notSampled = selection.notSampled,
                sampleSeed = seed,
                score = if (covered == 0) null else (killed + timedOut).toDouble() / covered,
                millis = System.currentTimeMillis() - started,
            ),
            mutants = ordered,
            baselineFailures = baselineFailures,
        )
        val out = outputDirectory.get().asFile.apply { mkdirs() }
        File(out, "results.json").writeText(reportJson.encodeToString(Report.serializer(), report))
        val text = TextReport.render(report)
        File(out, "summary.txt").writeText(text)
        logger.lifecycle(text)
        logger.lifecycle("Mutation report: ${File(out, "results.json")}")
        File(work, RANDOM_SEED_FILE).delete()
    }

    private fun timeoutFor(test: TestCase): Long =
        (test.millis * testTimeoutFactor.get()).toLong() + testTimeoutConstantMillis.get()

    private fun describe(test: TestCase): String {
        val method = Regex("""\[(?:method|test-template|test-factory):([^(\]]+)""").findAll(test.uid).lastOrNull()?.groupValues?.get(1)
        return if (method != null) "${test.className}#$method" else test.className
    }

    private class Selection(
        val selected: List<Site>,
        val rules: Map<Long, List<String>>,
        val notExpected: Int,
        val ignored: Int,
        val notSelected: Int,
        val excludedCalls: Int,
        val notSampled: Int,
    )

    /**
     * The configured seed, or with samples and no configured seed, a random seed. The random seed is
     * stored until the run completes, so an interrupted run resumes with the same sample.
     */
    private fun resolveSampleSeed(work: File): Long? {
        sampleSeed.orNull?.let { return it }
        if (samples.get().isEmpty()) return null
        val file = File(work, RANDOM_SEED_FILE)
        val seed = file.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
            ?: kotlin.random.Random.nextLong(1, 1_000_000_000).also { file.writeText(it.toString()) }
        logger.lifecycle("Sample seed $seed. Pass -P${MutationPlugin.SAMPLE_SEED_PROPERTY}=$seed to run the same sample again.")
        return seed
    }

    private fun select(repository: File, ruleSpecs: List<RuleSpec>, seed: Long?): Selection {
        val moduleDirs = modules.get()
        val classificationData = classification.orNull?.asFile?.takeIf { it.isFile }?.let(Classification::read) ?: Classification.Empty
        val moduleExpectation = moduleExpectations.get().mapValues { Expectation.decode(it.value) }
        val changedLines = changedSince.orNull?.let { changedLines(repository, it) }
        val wantedModules = onlyModules.orNull?.toSet()?.takeIf { it.isNotEmpty() }
        val ignoredIds = ignored.get().keys

        val wantedOperators = runOperators.getOrElse(emptyList()).toSet().takeIf { it.isNotEmpty() }
        val sites = Manifests.read(manifestDirectories.files, repository, moduleDirs)
            .filter { wantedModules == null || it.module in wantedModules }
            .filter { wantedOperators == null || it.operator in wantedOperators }
            .filter { changedLines == null || it.line in changedLines[it.path].orEmpty() }
            .map { site ->
                val constructs = classificationData.constructsAt(site.path, site.line)
                val expectation = classificationData.expectationOf(constructs)
                    ?: site.module?.let { moduleExpectation[it] }
                    ?: Expectation.Unclassified
                site.copy(constructs = constructs, expectation = expectation.without(excludedCategories.get()))
            }
        File(workDirectory.get().asFile, "sites.json").writeText(reportJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(SiteListing.serializer()),
            sites.map { site ->
                SiteListing(
                    id = site.hexId, path = site.path, line = site.line, operator = site.operator, variant = site.variant, text = site.text,
                    callee = site.callee, constructs = site.constructs, rules = ruleSpecs.filter { it.selects(site) }.map { it.id },
                )
            },
        ))
        warnUnmatchedCallPatterns(sites, ruleSpecs)
        val callPatterns = excludedCalls.get()
        val fractions = samples.get()
        var notSelected = 0
        var ignoredCount = 0
        var notExpected = 0
        var excludedCallCount = 0
        var notSampled = 0
        val kept = mutableListOf<Site>()
        val rulesBySite = mutableMapOf<Long, List<String>>()
        for (site in sites) {
            val matching = ruleSpecs.filter { it.selects(site) }.map { it.id }
            when {
                site.callee != null && callPatterns.any { RuleSpec.matchesCall(it, site.callee) } -> excludedCallCount++
                matching.isEmpty() -> notSelected++
                site.hexId in ignoredIds -> ignoredCount++
                site.expectation is Expectation.None -> notExpected++
                seed != null && !Sampling.keeps(site.id, fractions[site.operator] ?: 1.0, seed) -> notSampled++
                else -> { kept += site; rulesBySite[site.id] = matching }
            }
        }
        return Selection(kept, rulesBySite, notExpected, ignoredCount, notSelected, excludedCallCount, notSampled)
    }

    /** A pattern that matches no removed call is usually misspelt; the run continues. */
    private fun warnUnmatchedCallPatterns(sites: List<Site>, ruleSpecs: List<RuleSpec>) {
        val callees = sites.mapNotNull { it.callee }.toSet()
        val patterns = excludedCalls.get().map { it to "mutation { excludeCalls() }" } +
            ruleSpecs.flatMap { rule -> rule.excludedCalls.map { it to "rule(\"${rule.id}\") { excludeCalls() }" } }
        for ((pattern, source) in patterns) {
            if (callees.none { RuleSpec.matchesCall(pattern, it) }) {
                logger.warn("$source pattern \"$pattern\" matches no removed call in the compiled modules.")
            }
        }
    }

    private fun Site.result(status: Status, selection: Selection, fingerprint: String) = MutantResult(
        id = hexId,
        path = path,
        module = module,
        line = line,
        operator = operator,
        variant = variant,
        declaration = declaration,
        text = text,
        callee = callee,
        constructs = constructs,
        expected = (expectation as? Expectation.AnyOf)?.categories?.sorted().orEmpty(),
        rules = selection.rules[id].orEmpty(),
        status = status.label,
        fingerprint = fingerprint,
    )

    private fun changedLines(repository: File, ref: String): Map<String, Set<Int>> {
        val process = ProcessBuilder("git", "diff", "--unified=0", "--no-color", ref, "--", "*.kt")
            .directory(repository).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readLines()
        check(process.waitFor() == 0) { "git diff $ref failed: ${output.take(5).joinToString(" ")}" }
        val changed = mutableMapOf<String, MutableSet<Int>>()
        var file: String? = null
        val hunk = Regex("""^@@ -\S+ \+(\d+)(?:,(\d+))? @@""")
        for (line in output) {
            when {
                line.startsWith("+++ ") -> file = line.removePrefix("+++ ").removePrefix("b/").takeIf { it != "/dev/null" }
                line.startsWith("@@") -> {
                    val match = hunk.find(line) ?: continue
                    val start = match.groupValues[1].toInt()
                    val count = match.groupValues[2].ifEmpty { "1" }.toInt()
                    file?.let { f -> changed.getOrPut(f) { mutableSetOf() }.addAll(start until start + count) }
                }
            }
        }
        return changed
    }

    private fun fingerprint(specs: List<TestSpec>, seed: Long?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        manifestDirectories.asFileTree.files.sortedBy { it.path }.forEach { digest.update(it.readBytes()) }
        rules.get().forEach { digest.update(it.toByteArray()) }
        runOperators.getOrElse(emptyList()).forEach { digest.update(it.toByteArray()) }
        excludedCalls.get().sorted().forEach { digest.update(it.toByteArray()) }
        samples.get().toSortedMap().forEach { (operator, fraction) -> digest.update("$operator=$fraction".toByteArray()) }
        digest.update(seed.toString().toByteArray())
        digest.update(testTimeoutFactor.get().toString().toByteArray())
        digest.update(testTimeoutConstantMillis.get().toString().toByteArray())
        for (spec in specs) {
            digest.update(spec.toString().toByteArray())
            (spec.classpath + spec.testClassesDirs).map(::File).forEach { root ->
                root.walkTopDown().filter { it.isFile }.forEach { digest.update("${it.path}:${it.length()}:${it.lastModified()}".toByteArray()) }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun readJournal(journal: File, fingerprint: String): Map<Long, MutantResult> {
        if (!journal.isFile) return emptyMap()
        val results = journal.readLines().mapNotNull { line ->
            runCatching { lineJson.decodeFromString(MutantResult.serializer(), line) }.getOrNull()
        }
        if (results.isEmpty() || results.any { it.fingerprint != fingerprint }) return emptyMap()
        logger.lifecycle("Resuming: ${results.size} mutant results from an interrupted run with the same inputs.")
        return results.associateBy { java.lang.Long.parseUnsignedLong(it.id, 16) }
    }

    private companion object {
        const val SANITY_INTERVAL = 50
        const val SANITY_TESTS = 20
        const val MAX_RERUNS = 3
        const val RANDOM_SEED_FILE = "sample-seed.txt"
    }
}
