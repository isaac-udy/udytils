package dev.isaacudy.udytils.permissions

import dev.isaacudy.udytils.core.generated.resources.Res
import dev.isaacudy.udytils.core.generated.resources.permission_name_bluetoothConnect
import dev.isaacudy.udytils.core.generated.resources.permission_name_bluetoothScan
import dev.isaacudy.udytils.core.generated.resources.permission_name_camera
import dev.isaacudy.udytils.core.generated.resources.permission_name_locationCoarse
import dev.isaacudy.udytils.core.generated.resources.permission_name_locationFine
import dev.isaacudy.udytils.core.generated.resources.permission_name_phone
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
sealed interface Permission {
    val name: StringResource

    object Location {
        @Serializable
        data object Precise : Permission {
            override val name: StringResource = Res.string.permission_name_locationFine
        }

        @Serializable
        data object Approximate : Permission {
            override val name: StringResource = Res.string.permission_name_locationCoarse
        }
    }

    @Serializable
    data object Camera : Permission {
        override val name: StringResource = Res.string.permission_name_camera
    }

    object Bluetooth {
        @Serializable
        data object Scan : Permission {
            override val name: StringResource = Res.string.permission_name_bluetoothScan
        }

        @Serializable
        data object Connect : Permission {
            override val name: StringResource = Res.string.permission_name_bluetoothConnect
        }
    }

    @Serializable
    data object Phone : Permission {
        override val name: StringResource = Res.string.permission_name_phone
    }
}

expect fun hasPermission(permission: Permission): Boolean
