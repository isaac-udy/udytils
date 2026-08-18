> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/dev/isaacudy/udytils/rules/atlas/` and the `*.examples.md` files beside them.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# [Atlas Modules](../src/main/kotlin/dev/isaacudy/udytils/rules/atlas/AtlasModules.kt)

The atlas family is a UI atlas generator: a scanner/assembler core library and a Gradle plugin that wraps it. It is fully standalone within udytils.

##### Rules

* The atlas family must not depend on any other udytils family
    * **Why:** the atlas scans any Enro+Paparazzi project without requiring the rest of udytils on the classpath; importing another family would entangle them
