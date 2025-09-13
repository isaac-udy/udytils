package dev.isaacudy.udytils.samples

import dev.enro.annotations.NavigationComponent
import dev.enro.controller.NavigationComponentConfiguration
import dev.isaacudy.udytils.UdytilsConfig
import udytils.samples.generated.resources.Res
import udytils.samples.generated.resources.allStringResources

@NavigationComponent
object SamplesNavigation : NavigationComponentConfiguration() {
    init {
        UdytilsConfig.registerResources(Res.allStringResources)
    }
}