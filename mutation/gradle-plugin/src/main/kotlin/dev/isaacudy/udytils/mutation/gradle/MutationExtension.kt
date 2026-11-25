package dev.isaacudy.udytils.mutation.gradle

import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * The `mutation { }` block of the root project: the catalog rules that select mutants, the
 * mutants excluded as equivalent, and the architecture data that maps each mutant to the test
 * categories expected to kill it.
 */
abstract class MutationExtension {
    /** Package prefixes the compiler plugin mutates, for example `feature` or `feature.*`. */
    abstract val packages: SetProperty<String>

    /**
     * The operators the compiler plugin compiles in; by default every operator in [Operators.defaults].
     * A change recompiles every mutated module. Set the list with [operators].
     */
    abstract val operators: SetProperty<String>

    /** Compiles in only these operators, for example `operators("negate-condition", "remove-call")`. */
    fun operators(vararg ids: String) {
        Operators.requireKnown(ids.toList(), "mutation { operators() }")
        operators.set(ids.toSet())
    }

    /** The `classification.json` that `classifyArchitecture` writes; without it, no mutant has a Construct. */
    abstract val classification: RegularFileProperty

    /**
     * Module path to the test categories expected for lines that no Construct claims, encoded as
     * `anyOf:<category>,<category>` or `none:<reason>`.
     */
    abstract val moduleExpectations: MapProperty<String, String>

    /**
     * A test in a mutant run times out after its baseline duration times this factor plus
     * [testTimeoutConstantMillis]. A timed-out test counts as a kill.
     */
    abstract val testTimeoutFactor: Property<Double>

    abstract val testTimeoutConstantMillis: Property<Long>

    /**
     * Test categories (JUnit tags) that never count: a test with one of these tags does not run
     * against mutants, and a mutant whose Constructs expect only these categories is not run.
     */
    abstract val excludedCategories: SetProperty<String>

    /** JUnit Platform engine IDs whose tests never run against mutants, for example `junit-vintage`. */
    abstract val excludedEngines: SetProperty<String>

    internal abstract val encodedRules: ListProperty<String>
    internal abstract val ignored: MapProperty<String, String>

    /** Adds a catalog rule. A mutant runs when at least one rule selects it. */
    fun rule(id: String, configure: Action<MutationRule>) {
        require(id.isNotBlank()) { "rule() requires an id." }
        val rule = MutationRule(id).also { configure.execute(it) }
        encodedRules.add(rule.encode())
    }

    /** Excludes the mutant with hexadecimal [id] (from the report) from every run, for [reason]. */
    fun ignore(id: String, reason: String) {
        require(reason.isNotBlank()) { "ignore(\"$id\") requires a reason." }
        ignored.put(id.lowercase(), reason)
    }
}

/**
 * Selects mutants. Each non-empty selector list must match: [constructs] against the Construct
 * IDs of the mutant's declaration, [packages] against its package, [files] against its path from
 * the repository root. A pattern ending in `*` matches any suffix; a file pattern also accepts `**`.
 */
class MutationRule internal constructor(val id: String) {
    internal val constructs = mutableListOf<String>()
    internal val packages = mutableListOf<String>()
    internal val files = mutableListOf<String>()
    internal val excludedConstructs = mutableListOf<String>()
    internal val excludedFiles = mutableListOf<String>()
    internal val operators = mutableListOf<String>()

    fun constructs(vararg patterns: String) { constructs += patterns }
    fun packages(vararg patterns: String) { packages += patterns }
    fun files(vararg patterns: String) { files += patterns }
    fun excludeConstructs(vararg patterns: String) { excludedConstructs += patterns }
    fun excludeFiles(vararg patterns: String) { excludedFiles += patterns }

    /** Limits the rule to these operators, for example `operators("remove-call")`; by default every compiled operator applies. */
    fun operators(vararg ids: String) {
        Operators.requireKnown(ids.toList(), "rule(\"$id\") { operators() }")
        operators += ids
    }

    internal fun encode(): String = RuleSpec(id, constructs, packages, files, excludedConstructs, excludedFiles, operators).encode()
}
