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

    // A mutant runs when a rule selects it.
    rule("domain") { constructs("ClientDomain.*", "ServerDomain.*") }
    rule("parsers") { files("**/parse/*.kt") }

    // An equivalent mutant: no test can tell it from the original code.
    ignore("4c8277a303ecb08a", "the condition is always true for a non-empty list")
}
```

Then run:

```
./gradlew runMutants
./gradlew runMutants -Pmutation.changedSince=origin/main   # mutants on lines changed since the ref
./gradlew runMutants -Pmutation.modules=:feature:core:server
```

## Mutation mode

The compiler plugin, the runtime dependency and the test specs exist only in a build that requests
`runMutants`. Every other build compiles the original code. The plugin mutates the JVM main
compilation of each Kotlin module. Sources under a module's build directory, such as KSP output,
are not mutated.

Operators:
- `negate-condition`: the condition of an `if`, a `when` branch, a `while` or a `do-while`.
- `negate-boolean-return`: the value of a `return` in a function that returns `Boolean`.

A mutant ID is a 64-bit hash of the enclosing declaration's signature, the file name, the operator,
the mutated source text, and the occurrence of that text in the declaration. Adding or removing a
site leaves every other ID unchanged.

## Runner

For each JVM test task (`jvmTest` or `test`):

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

An interrupted run resumes from `build/mutation/results.jsonl` when the manifests, the rules and
the test classpaths are unchanged.

Expected categories come from the Constructs' `testedBy` in `classification.json`, or from
`moduleExpectations` for lines that no Construct claims. A mutant with no expectation accepts a test
in any category. A mutant whose Constructs expect no tests is not run.

## Output

`build/reports/mutation/`:
- `results.json`: one entry per mutant: ID, file and line, operator, source text, Constructs,
  expected categories, matching rules, status (`killed`, `timed-out`, `survived`, `uncovered`), the
  killing test and its failure, and the number of tests run.
- `summary.txt`: counts and the survived and uncovered mutants; also printed by `runMutants`.

`build/mutation/sites.json` lists every compiled mutant site, selected or not, with its
Constructs and matching rules.

Limits: JVM test tasks only. A mutant in a source set that the JVM target does not compile, such as
`iosMain`, is not compiled or reported.
