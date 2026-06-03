package dev.isaacudy.udytils.samples.catalog

import dev.enro.NavigationKey

data class Sample(
    val title: String,
    val description: String,
    val key: NavigationKey,
)
