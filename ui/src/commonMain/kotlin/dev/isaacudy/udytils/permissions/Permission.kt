package dev.isaacudy.udytils.permissions

import dev.isaacudy.udytils.ui.generated.resources.Res
import dev.isaacudy.udytils.ui.generated.resources.permission_name_bluetoothConnect
import dev.isaacudy.udytils.ui.generated.resources.permission_name_bluetoothScan
import dev.isaacudy.udytils.ui.generated.resources.permission_name_camera
import dev.isaacudy.udytils.ui.generated.resources.permission_name_location
import dev.isaacudy.udytils.ui.generated.resources.permission_name_notifications
import dev.isaacudy.udytils.ui.generated.resources.permission_name_phone
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
sealed interface Permission {
    val name: StringResource

    @Serializable
    data class Location(
        val requirePrecise: Boolean,
        val requireBackground: Boolean,
    ): Permission {
        override val name: StringResource get() = Res.string.permission_name_location
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

    @Serializable
    data object Notifications : Permission {
        override val name: StringResource = Res.string.permission_name_notifications
    }
}

expect fun hasPermission(permission: Permission): Boolean
