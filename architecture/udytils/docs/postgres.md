> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/dev/isaacudy/udytils/rules/postgres/` and the `*.examples.md` files beside them.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# [Postgres Modules](../src/main/kotlin/dev/isaacudy/udytils/rules/postgres/PostgresModules.kt)

The postgres family is a JVM-only Postgres + Exposed toolkit: runtime column types and migration/notification helpers, a build-time codegen engine, a Gradle plugin, and dev/test helpers. It is fully standalone within udytils.

##### Rules

* The postgres family must not depend on any other udytils family
    * **Why:** a server can adopt the postgres toolkit without inheriting KMP, Compose or rpc dependencies — and that only stays true while the family imports nothing from the rest of udytils
