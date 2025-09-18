package dev.isaacudy.udytils.permissions

import dev.isaacudy.udytils.core.generated.resources.Res
import dev.isaacudy.udytils.core.generated.resources.permision_name_camera
import dev.isaacudy.udytils.core.generated.resources.permision_name_locationCoarse
import dev.isaacudy.udytils.core.generated.resources.permision_name_locationFine
import dev.isaacudy.udytils.core.generated.resources.permision_name_nearbyDevices
import dev.isaacudy.udytils.core.generated.resources.permision_name_phone
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
sealed interface Permission {
    val name: StringResource

    object Location {
        @Serializable
        data object Precise : Permission {
            override val name: StringResource = Res.string.permision_name_locationFine
        }

        @Serializable
        data object Approximate : Permission {
            override val name: StringResource = Res.string.permision_name_locationCoarse
        }
    }

    @Serializable
    data object Camera : Permission {
        override val name: StringResource = Res.string.permision_name_camera
    }

    @Serializable
    data object NearbyDevices : Permission {
        override val name: StringResource = Res.string.permision_name_nearbyDevices
    }

    @Serializable
    data object Phone : Permission {
        override val name: StringResource = Res.string.permision_name_phone
    }
}

expect fun hasPermission(permission: Permission): Boolean
