> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/dev/isaacudy/udytils/rules/urpc/` and the `*.examples.md` files beside them.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# [Urpc Modules](../src/main/kotlin/dev/isaacudy/udytils/rules/urpc/UrpcModules.kt)

The urpc family is a typed RPC framework: protocol (shared contract types), client (KMP Ktor client), server (JVM Ktor routing), processor (KSP codegen) and koin (server DI glue). The protocol module is the only thing the two sides share.

##### Rules

* The urpc protocol module may depend only on the core module
    * **Why:** protocol is what generated code and both transports compile against; if it reached into client or server, every consumer would drag in both transport stacks
* The urpc client and server modules must not depend on each other
    * **Why:** a KMP client that pulled in Ktor server code (or vice versa) could not compile for its targets; the only shared vocabulary is the protocol module
* The urpc family must not depend on the ui, postgres, or architecture families
    * **Why:** urpc is transport infrastructure; UI rendering and database access belong to the application wiring the pieces together, not to the framework
