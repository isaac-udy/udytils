> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the RuleGroups and Constructs in this project.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# Rule index

The complete catalog, one row per Construct or Rule. IDs are based on the object/property that declares the entry (see the [README](../README.md)). Enforcement markers link to the declaring source and are explained below the table.

| Rule | Statement | Enforcement |
| --- | --- | --- |
| `CoreModule.standalone` | The core module must not depend on any other udytils module | [tested](../src/main/kotlin/dev/isaacudy/udytils/rules/core/CoreModule.kt) |
| `UiModule.dependsOnlyOnCore` | The ui module may only depend on the core module | [tested](../src/main/kotlin/dev/isaacudy/udytils/rules/ui/UiModule.kt) |
| `UrpcModules.protocolIsTheSharedContract` | The urpc protocol module may depend only on the core module | [tested](../src/main/kotlin/dev/isaacudy/udytils/rules/urpc/UrpcModules.kt) |
| `UrpcModules.clientAndServerStayApart` | The urpc client and server modules must not depend on each other | [tested](../src/main/kotlin/dev/isaacudy/udytils/rules/urpc/UrpcModules.kt) |
| `UrpcModules.staysOutOfOtherFamilies` | The urpc family must not depend on the ui, postgres, or architecture families | [tested](../src/main/kotlin/dev/isaacudy/udytils/rules/urpc/UrpcModules.kt) |
| `PostgresModules.standaloneFamily` | The postgres family must not depend on any other udytils family | [tested](../src/main/kotlin/dev/isaacudy/udytils/rules/postgres/PostgresModules.kt) |
| `ArchitectureModules.standaloneFamily` | The architecture family must not depend on any other udytils family | [tested](../src/main/kotlin/dev/isaacudy/udytils/rules/architecture/ArchitectureModules.kt) |

## Enforcement status

Each status is derived from how the entry is declared:

| Status | Meaning | Declared as |
| --- | --- | --- |
| `tested` | A test enforces the Rule and fails citing its ID. | a `rule` ending in `scope { }` / `constrain { }` / `moduleGraph { }` / `enforcedBy(...)` |
| `construct` | A classification. A declaration matching no Construct (or more than one) fails the RuleGroup's exhaustiveness test. | a `Construct(...)`'s requirements |
| `unverifiable` | A mandatory Rule that tests can't reliably verify; enforced by review. | a `rule` ending in `unverifiable()` |
| `guidance` | An advisory statement; enforced by review. May declare an `audit { }`: a test that reports non-conforming code without ever failing. | `@Describe("…") val x by guidance` |
| `codegen` | Guaranteed by a code generator; there is nothing in source to test. | a `rule` ending in `codegen()` |
