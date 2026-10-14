> [!NOTE]
> **This file is generated. Do not edit it directly.**
> Generated from the `@Describe` annotations in `src/main/kotlin/dev/isaacudy/udytils/rules/ui/` and the `*.examples.md` files beside them.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :udytils-architecture:test`.

# [Ui Module](../src/main/kotlin/dev/isaacudy/udytils/rules/ui/UiModule.kt)

The ui module holds Compose Multiplatform components and navigation-based flows (errors, confirmations, permissions). It builds on core, on Enro, and on the viewmodel module, which holds ViewModel state without a Compose UI dependency and which ui re-exports. The server-side families are out of bounds for both.

##### Rules

* The ui and viewmodel modules may only depend on the core module
    * **Why:** ui exists to render core's state and error types; rpc transports, database code and the architecture framework have no business in UI code, and keeping them out keeps ui consumable by apps that use none of them
