> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/dev/isaacudy/udytils/rules/architecture/` and the `*.examples.md` files beside them.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# [Architecture Modules](../src/main/kotlin/dev/isaacudy/udytils/rules/architecture/ArchitectureModules.kt)

The architecture family is the architecture-as-code framework: the rule/docs engine (architecture-core), the exemption annotation (architecture-annotations), and the Gradle plugin. It is fully standalone within udytils — a consumer adopts it without touching the other families.

##### Rules

* The architecture family must not depend on any other udytils family
    * **Why:** the framework governs codebases that may use none of udytils' other libraries; any import from another family would make it unusable outside this repository's stack
