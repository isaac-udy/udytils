# Mutation

Mutation testing for Kotlin Multiplatform builds. A Kotlin IR compiler plugin compiles every
mutant into the production code once, behind a runtime switch. The runner selects one mutant at a
time and runs only the tests that execute that mutant, in one test JVM per test task.

## Modules

| Artifact | What it is |
|---|---|
| `mutation-runtime` | The switch that mutated code reads (`Mutants.active`) and the hit recorder (KMP) |
| `mutation-compiler-plugin` | Kotlin IR plugin that inserts the mutants and writes one manifest per source file |
| `mutation-junit-driver` | Runs JUnit Platform tests in one JVM while the runner switches the active mutant |
| `mutation-gradle-plugin` | Gradle plugin (`dev.isaacudy.udytils.mutation`): mutation mode, `runMutants`, the catalog DSL |

## Gradle plugin

Apply at the root project:

```kotlin
// build.gradle.kts (root)
plugins {
    id("dev.isaacudy.udytils.mutation") version "<version>"
}

mutation {
    // Package prefixes the compiler plugin mutates.
    packages.addAll("com.example", "feature")

    // Optional: Construct IDs and expected test categories from an architecture catalog.
    classification.set(layout.projectDirectory.file("architecture/build/reports/architecture/classification.json"))

    // Optional: the operators to compile in; by default every operator.
    operators("negate-condition", "boolean-return", "remove-call")

    // Optional: remove-call mutants of matching calls never run.
    excludeCalls("org.koin.*", "org.slf4j.Logger.*")

    // Optional: run a fraction of one operator's selected mutants.
    sample("remove-call", 0.25)

    // A mutant runs when a rule selects it.
    rule("domain") { constructs("ClientDomain.*", "ServerDomain.*") }
    rule("parsers") { files("**/parse/*.kt") }

    // Test categories (JUnit tags) and JUnit engines that never count as a kill.
    excludedCategories.add("snapshot")
    excludedEngines.add("junit-vintage")

    // An equivalent mutant: no test can tell it from the original code.
    ignore("4c8277a303ecb08a", "the condition is always true for a non-empty list")
}
```

Then run:

```
./gradlew runMutants
./gradlew runMutants -Pmutation.changedSince=origin/main   # mutants on lines changed since the ref
./gradlew runMutants -Pmutation.modules=:feature:core:server
./gradlew runMutants -Pmutation.hosts=android               # Android host tests in place of JVM tests
./gradlew runMutants -Pmutation.operators=remove-call       # only the mutants of these compiled operators
./gradlew runMutants -Pmutation.sample=remove-call=0.1      # 10% of the selected remove-call mutants
./gradlew runMutants -Pmutation.sampleSeed=2                # a different sample
```

Each module runs its mutants on one host. By default, a module with a JVM target uses its JVM tests,
and a module with only an Android target uses its Android host tests (`testAndroidHostTest`).
`-Pmutation.hosts=android` selects the Android host for every module with an Android target;
`-Pmutation.hosts=android,jvm` also keeps the JVM host for modules with no Android target.

## Mutation mode

The compiler plugin, the runtime dependency and the test specs exist only in a build that requests
`runMutants`. Every other build compiles the original code. The plugin mutates the JVM main
compilation of each Kotlin module, or the Android main compilation when the module runs on the
`android` host. Sources under a module's build directory, such as KSP output,
are not mutated.

### Operators

Each operator is one file in
[`compiler-plugin/.../operators`](compiler-plugin/src/main/kotlin/dev/isaacudy/udytils/mutation/compiler/operators),
an object that implements `MutationOperator`. `MutationOperators.all` lists them.

| ID | Change | Variants |
|---|---|---|
| `negate-condition` | negates the condition of an `if`, a `when` branch, a `while` or a `do-while` | `negated` |
| `boolean-return` | replaces the value a `Boolean` function returns | `→ true`, `→ false` |
| `empty-return` | replaces a returned value with `null`, `emptyList()`, `emptySet()`, `emptyMap()`, `emptySequence()`, `""` or `0`, by return type | one per type |
| `remove-call` | removes a call whose result is not used, a property assignment, a compound assignment or an increment of a property; a safe call (`a?.save()`) is not removed | `removed` |
| `conditionals-boundary` | `<` ↔ `<=`, `>` ↔ `>=` | one per comparison |
| `logical-connector` | `&&` ↔ `\|\|`, and each operand alone | three per expression |
| `elvis` | `a ?: b` → `b` | `→ right side` |

Every operator is compiled in by default. `mutation { operators("negate-condition", "remove-call") }`
compiles in only the named operators; a change recompiles the mutated modules. A rule's
`operators()` and `-Pmutation.operators=remove-call` select from the compiled operators without
recompiling. An unknown operator ID fails the build at configuration, and so does an ID that a rule
or `-Pmutation.operators` names but the build does not compile in.

### Excluded calls

Each `remove-call` mutant records the fully qualified name of the function or property it removes,
for example `org.koin.core.module.Module.single` or `kotlinx.coroutines.flow.MutableStateFlow.value`.
`excludeCalls("org.koin.*")` in the `mutation { }` block leaves out every matching mutant;
`excludeCalls()` inside a rule leaves the mutant out of that rule only. In a pattern, `*` matches any
characters, dots included: `*.Logger.info` matches `org.slf4j.Logger.info`. `runMutants` warns about a
pattern that matches no removed call in the compiled modules. Excluding calls requires no recompile.

### Sampling

`sample("remove-call", 0.1)` runs 10% of the selected `remove-call` mutants; an operator with no
sample runs every selected mutant. `-Pmutation.sample=remove-call=0.1,elvis=0.5` replaces the named
operators' fractions for one run. A mutant is in the sample when a hash of its ID and the seed falls
below the fraction, so the same seed selects the same mutants in every run, and a mutant added to the
code does not change which other mutants run. `sampleSeed` (default 0) or `-Pmutation.sampleSeed`
selects a different sample. A fraction outside (0, 1], or an operator that is unknown or not
compiled in, fails the build at configuration.

The plugin compiles each site's mutants in behind the runtime switch:

```kotlin
when {
    mutantActive(17) -> <replacement 1>
    mutantActive(18) -> <replacement 2>
    else -> <original>
}
```

A replacement contains a copy of the site's original children; only the `else` branch contains the
children's own mutants. A manifest stores the first 160 characters of a mutant's source text.

A mutant ID is a 64-bit hash of the enclosing declaration's signature, the file name, the operator,
the variant, the mutated source text, and the occurrence of that text in the declaration. Adding or removing a
site leaves every other ID unchanged.

## Runner

For each module's test task (`jvmTest` or `test` on the JVM host, `testAndroidHostTest` on the
Android host):

1. A baseline run with no mutant active records which mutants each test method executes, and each
   test's duration. A hit in class-level setup counts for every test of the class. A test that fails
   the baseline is excluded from every mutant.
2. Each selected mutant runs against the passing tests that executed the mutant and carry an
   expected category tag, fastest first. The run stops at the first failing test.
3. A test times out after its baseline duration × `testTimeoutFactor` (2.0) +
   `testTimeoutConstantMillis` (1 s). The driver interrupts the test thread at the timeout, so an
   `@Timeout` annotation does not delay the kill. A timed-out test counts as a kill. A driver that
   stops responding is restarted.
4. Every 50 mutants, the 20 fastest tests run with no mutant active. A failure means an earlier
   mutant left state in the JVM: the driver restarts and the last 50 mutants run again.
5. A mutant in a top-level or `object` property initialiser runs in a fresh JVM started with that
   mutant active, because the initialiser runs once per JVM.

An interrupted run resumes from `build/mutation/results.jsonl` when the manifests, the rules, the
excluded calls, the samples and the test classpaths are unchanged.

Expected categories come from the Constructs' `testedBy` in `classification.json`, or from
`moduleExpectations` for lines that no Construct claims. A mutant with no expectation accepts a test
in any category. A mutant whose Constructs expect no tests is not run.
`excludedCategories` removes categories from every expectation; a mutant whose Constructs expect only
excluded categories is not run. A test from an engine in `excludedEngines` is not discovered.

## Output

`build/reports/mutation/`:
- `results.json`: one entry per mutant: ID, file and line, operator, variant, source text, the
  removed call for `remove-call`, Constructs,
  expected categories, matching rules, status (`killed`, `timed-out`, `survived`, `uncovered`), the
  killing test and its failure, and the number of tests run.
- `summary.txt`: counts and the survived and uncovered mutants; also printed by `runMutants`.

`build/mutation/sites.json` lists every compiled mutant site, selected or not, with its
Constructs and matching rules.

Limits: JVM and Android host test tasks only. A mutant in a source set that neither host compiles,
such as `iosMain`, is not compiled or reported.
