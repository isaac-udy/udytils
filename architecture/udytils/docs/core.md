> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/dev/isaacudy/udytils/rules/core/` and the `*.examples.md` files beside them.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# [Core Module](../src/main/kotlin/dev/isaacudy/udytils/rules/core/CoreModule.kt)

The core module is the foundation of udytils: async state modelling, coroutine utilities, presentable errors, and file helpers. Every other family may depend on it; it depends on none of them.

##### Rules

* The core module must not depend on any other udytils module
    * **Why:** core sits at the bottom of the dependency graph, so a dependency on any other family would create a cycle or drag UI/server concerns into every consumer
    * **Note:** ui declares ViewModelState inside core's dev.isaacudy.udytils.state package, so this boundary is attributed by file location and checked against ui's unambiguous package roots
* The core module must not declare a Gradle dependency on any other project
    * **Why:** the import rule catches code-level coupling; this one catches a build-level dependency added before any import exists, which would still change what every core consumer transitively resolves
    * **Note:** the graph reads projects.x.y accessors and string-notation project dependencies; a dependency declared by maven coordinate resolved through dependencySubstitution is not visible to it
