> [!NOTE]
> **This file is generated. Do not edit it directly.**
> The introduction comes from the `@Describe` annotation on `UdytilsArchitecture` (`src/main/kotlin/dev/isaacudy/udytils/rules/UdytilsArchitecture.kt`); the remaining sections are provided by the udytils architecture system.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# The udytils architecture

udytils is not one library but a set of independent families — core, ui, urpc, postgres,
and the architecture framework — living in one repository. The one structural promise the
repository makes is that the families stay independent: core depends on nothing, ui builds
only on core, and the server-side families never leak into each other or into UI code.

The rules below express those boundaries as import rules over each family's sources, so a
dependency that would entangle two families fails the build instead of quietly becoming
load-bearing.

- [Core Module](docs/core.md)
- [Ui Module](docs/ui.md)
- [Urpc Modules](docs/urpc.md)
- [Postgres Modules](docs/postgres.md)
- [Architecture Modules](docs/architecture.md)
- [Authoring rules](docs/authoring.md)
- [Architecture exceptions](docs/exceptions.md)
- [Rule index](docs/rule-index.md)

## Rules

- [Core Module](docs/core.md)
- [Ui Module](docs/ui.md)
- [Urpc Modules](docs/urpc.md)
- [Postgres Modules](docs/postgres.md)
- [Architecture Modules](docs/architecture.md)

## Reference

- [Rule index](docs/rule-index.md): An index of all rules used in this project
- [Authoring rules](docs/authoring.md): A guide for authoring new architecture rules
- [Architecture exceptions](docs/exceptions.md): A guide for using `@ArchitectureException` to ignore rules

---

# Architecture Testing System

This project uses the [udytils architecture system](https://github.com/isaac-udy/udytils) to define, test, and document its architecture rules. Rules are declared in Kotlin code, built on the Konsist library, and structured using the following types:

- **RuleGroup:** names and defines a set of Constructs, Rules, and Guidance.
  - A RuleGroup may be scoped to a particular package pattern. Scoping a RuleGroup to a package pattern will require all associated Constructs to be defined in a package matching that pattern.
- **Construct:** names and defines the Rules and Guidance for a code-level construct (such as a class, interface, function or property).  
  - A Construct must be associated with a RuleGroup.
  - A Construct defines a set of requirements in its constructor. If a piece of code matches the requirements for a particular Construct, it will be required to meet the rules associated with that construct. 
  - To provide example code for a Construct, create a `<Construct>.examples.md` file next to the associated `<Construct.kt>` file.
- **Rule:** a mandatory statement about a `Construct` or `RuleGroup`.
- **Guidance:** an advisory statement about a `Construct` or `RuleGroup`.
 
Documentation for RuleGroups and Constructs is recorded by annotating the RuleGroup or Construct with the `@Describe` annotation. Documentation for Rules and Guidance is also provided by annotating the Rule or Guidance statement with `@Describe` but Rules and Guidance also provide the ability to add "rationale" and "notes" through functions in their builder definitions.

Every Rule/Guidance/Construct has a stable ID based on the object/property that declares it:

| ID | Reads as |
| --- | --- |
| `CoreModule.standalone` | a RuleGroup-level rule (not tied to a Construct) |

Test failures, the [rule index](docs/rule-index.md), and [architecture exceptions](docs/exceptions.md) reference rules by ID. Construct requirements don't have their own IDs, they belong to their Construct.

This README and everything under `docs/` is generated based on the RuleGroups/Constructs in this project. Never edit these files directly. Read [authoring](docs/authoring.md) before adding rules.

## Run the tests

```
./gradlew :udytils-architecture:test
```

## Regenerate the documentation

```
UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test
```

Run this after changing the catalog or an examples file. The tests fail if the generated documentation is manually edited, or if the documentation references a rule that doesn't exist.
