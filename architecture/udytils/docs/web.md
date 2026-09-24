> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/dev/isaacudy/udytils/rules/web/` and the `*.examples.md` files beside them.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# [Web Modules](../src/main/kotlin/dev/isaacudy/udytils/rules/web/WebModules.kt)

The web family serves server-rendered HTML: `htmx` (typed htmx and Alpine attributes for kotlinx.html, Ktor request and response helpers, out-of-band list updates, bundled scripts) and `html-snapshot` (golden-file assertions over normalised HTML). Both are JVM-only and standalone within udytils.

##### Rules

* The web family must not depend on any other udytils family, and its two modules must not depend on each other
    * **Why:** a server-rendered application takes either module without inheriting KMP, Compose or rpc dependencies, and a test-only snapshot library stays out of production classpaths
