# Udytils Architecture

Architecture-as-code for Kotlin projects: declare your architecture as a catalog of
Kotlin objects — layers, constructs, rules, guidance — and the framework turns it into
**runnable JUnit 5 tests** (via [Konsist](https://github.com/LemonAppDev/konsist)) and
**generated Markdown documentation** that is golden-tested against the catalog, so docs
and rules can never drift apart.

## Artifacts

| Coordinates | What it is | Where it goes |
|---|---|---|
| `dev.isaacudy.udytils:architecture-core` | The rule DSL, test harnesses, docs generator (JVM) | the catalog module's classpath (added by the plugin) |
| `dev.isaacudy.udytils:architecture-annotations` | `@ArchitectureException` (KMP) | any governed module that needs an exemption |
| `dev.isaacudy.udytils.architecture` (Gradle plugin) | `architectureTest` source set + tasks | the catalog module |

## Setup

Pick (or create) a JVM module to own the catalog — the rules are ordinary Kotlin in its
`main` source set:

```kotlin
// tools/architecture/build.gradle.kts
plugins {
    kotlin("jvm")
    id("dev.isaacudy.udytils.architecture") version "<version>"
}

architecture {
    definition.set("com.example.architecture.MyArchitecture")
}
```

The plugin creates an `architectureTest` source set, generates the test classes from
your definition (nothing test-related is checked in), and registers two tasks:

- **`verifyArchitecture`** — runs every rule against the codebase. Not attached to
  `check`; wire it into CI explicitly.
- **`updateArchitectureDocumentation`** — regenerates the docs (README + `docs/`) from
  the catalog. A golden test fails `verifyArchitecture` when committed docs are stale.

## Declaring an architecture

```kotlin
@Describe("# My Architecture\n\nWhat the layers are and why. {{toc}}")
object MyArchitecture : ArchitectureDefinition(
    groups = listOf(DomainLayer, ServicesLayer),
    scope = { Konsist.scopeFromProject().slice { it.path.contains("/feature/") } },
    membership = { it.residesInGovernedCode() },
    docs = DocsConfig(module = "tools/architecture"),
)

@Describe("The domain layer holds pure business types …")
object DomainLayer : RuleGroup(
    inPackage = "com.example..domain..",
    constructs = listOf(Repository),
) {
    @Describe("The domain layer must not depend on feature modules")
    val noFeatureDeps by rule { moduleGraph { /* … */ } }
}

@Describe("A Repository mediates access to a single aggregate …")
object Repository : Construct<DomainLayer>(
    requirements = listOf(isClass, hasNameEndingWith("Repository")),
) {
    @Describe("A Repository must not inject other Repositories")
    val noRepositoryInjection by rule {
        constrain { declaration -> /* return violations */ }
    }

    @Describe("A Repository should keep its public surface small")
    val smallSurface by guidance
}
```

The building blocks:

- **`RuleGroup`** — one layer. A group scoped with `inPackage` gains an exhaustiveness
  test: every top-level declaration in that package must match exactly one of its
  Constructs.
- **`Construct`** — a named code shape. Its `requirements` decide *identity* ("is this a
  Repository?"), not correctness — a declaration matching no Construct fails
  exhaustiveness instead.
- **`rule { }`** — a mandatory statement, tested via `constrain { }` (per declaration),
  `scope { }` (whole scope), `moduleGraph { }` (module dependencies), `enforcedBy(...)`
  (another rule covers it), or declared `unverifiable()` (documented, enforced by
  review). `rationale("…")` and `note("…")` enrich the generated docs.
- **`guidance`** — advisory (may/should). Guidance can carry an **audit**: a test that
  reports findings without ever failing the build.

Module-dependency rules read the Gradle build files; exemptions there use
`// architecture-exception: RuleId` comments, while Kotlin declarations use
`@ArchitectureException(ruleIds = [...], reason = "…")` from `architecture-annotations`.
Every exemption must carry a reason, and the framework's docs treat them as temporary,
reviewed debts.

## Generated documentation

`updateArchitectureDocumentation` writes, per layer, a Markdown page compiled from the
`@Describe` texts and optional `<Name>.examples.md` files, plus a rule index and the
framework-owned guides on authoring rules and using exemptions. The result is a
self-describing architecture handbook — the same catalog produces the tests and the
book, and the golden test keeps them in lockstep.

## Status

Young framework, extracted from a real production codebase. The rule DSL and generated
docs are dogfooded there; expect the API to evolve while it settles.
