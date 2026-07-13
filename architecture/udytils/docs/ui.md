> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/dev/isaacudy/udytils/rules/ui/` and the `*.examples.md` files beside them.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# [Ui Module](../src/main/kotlin/dev/isaacudy/udytils/rules/ui/UiModule.kt)

The ui module holds Compose Multiplatform components, ViewModel state, and navigation-based flows (errors, confirmations, permissions). It builds on core and on Enro; the server-side families are out of bounds.

##### Rules

* The ui module may only depend on the core module
    * **Why:** ui exists to render core's state and error types; rpc transports, database code and the architecture framework have no business in UI code, and keeping them out keeps ui consumable by apps that use none of them
