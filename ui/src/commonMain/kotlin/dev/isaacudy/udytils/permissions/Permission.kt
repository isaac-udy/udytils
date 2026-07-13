package dev.isaacudy.udytils.permissions

import dev.isaacudy.udytils.ui.generated.resources.Res
import dev.isaacudy.udytils.ui.generated.resources.permission_name_bluetoothConnect
import dev.isaacudy.udytils.ui.generated.resources.permission_name_bluetoothScan
import dev.isaacudy.udytils.ui.generated.resources.permission_name_camera
import dev.isaacudy.udytils.ui.generated.resources.permission_name_location
import dev.isaacudy.udytils.ui.generated.resources.permission_name_microphone
import dev.isaacudy.udytils.ui.generated.resources.permission_name_notifications
import dev.isaacudy.udytils.ui.generated.resources.permission_name_phone
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * A runtime permission that can be checked with [hasPermission] / [rememberHasPermission] and
 * requested by opening a [RequestPermissionDestination].
 *
 * ## Platform support
 * - **Android**: implemented. Checks map to the corresponding manifest permissions, and requests
 *   use the system permission dialog (including a settings flow for permanent denial).
 * - **iOS**: implemented. Checks map to the matching framework authorization APIs
 *   ([Notifications] and [Phone] currently always report granted), and requests trigger the
 *   system prompt for [Location], [Camera] and [Microphone].
 * - **Desktop JVM / wasmJs**: NOT implemented. The `actual` implementations are placeholders, so
 *   calling [hasPermission] (directly or via [rememberHasPermission]) on desktop JVM or wasm
 *   currently throws [NotImplementedError] (`TODO("Not yet implemented")`).
 *
 * [name] is a localised, human-readable name for the permission, suitable for request UI.
 */
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
    data object Microphone : Permission {
        override val name: StringResource = Res.string.permission_name_microphone
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

/**
 * Returns whether [permission] is currently granted, without prompting the user. To prompt, open
 * a [RequestPermissionDestination]; to observe grants across lifecycle changes in Compose, use
 * [rememberHasPermission].
 *
 * Implemented on Android and iOS. The desktop JVM and wasmJs `actual`s are placeholders, so
 * calling this on those platforms currently throws [NotImplementedError].
 */
expect fun hasPermission(permission: Permission): Boolean
