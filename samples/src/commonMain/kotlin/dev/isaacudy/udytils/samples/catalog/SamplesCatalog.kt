package dev.isaacudy.udytils.samples.catalog

import dev.isaacudy.udytils.samples.confirmation.ConfirmationSamplesDestination
import dev.isaacudy.udytils.samples.state.AsyncStateSamplesDestination

object SamplesCatalog {
    val categories: List<SampleCategory> = listOf(
        SampleCategory(
            title = "core",
            description = "Utilities from the core module (no Compose dependency).",
            samples = listOf(
                Sample(
                    title = "AsyncState",
                    description = "Idle / Loading / Success / Error state for async work",
                    key = AsyncStateSamplesDestination,
                ),
            ),
        ),
        SampleCategory(
            title = "ui",
            description = "UI components and Compose-tied utilities.",
            samples = listOf(
                Sample(
                    title = "ConfirmationDestination",
                    description = "AlertDialog-style confirmation prompt",
                    key = ConfirmationSamplesDestination,
                ),
            ),
        ),
    )
}
