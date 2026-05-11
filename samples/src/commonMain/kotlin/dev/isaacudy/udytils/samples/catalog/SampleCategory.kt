package dev.isaacudy.udytils.samples.catalog

data class SampleCategory(
    val title: String,
    val description: String? = null,
    val samples: List<Sample>,
)
