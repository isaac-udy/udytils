package dev.isaacudy.udytils.permissions

import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways

actual fun hasPermission(permission: Permission): Boolean {
    when (permission) {
        Permission.Bluetooth.Connect,
        Permission.Bluetooth.Scan -> {
            return when (CBManager.authorization) {
                CBManagerAuthorizationAllowedAlways -> true
                else -> false
            }
        }

        Permission.Camera -> {
            val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
            return when (status) {
                AVAuthorizationStatusAuthorized -> true
                else -> false
            }
        }

        is Permission.Location -> {
            return IosPermissionObserver.locationState().grants(permission)
        }

        Permission.Notifications -> {
            return true
        }

        Permission.Microphone -> {
            val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
            return status == AVAuthorizationStatusAuthorized
        }

        Permission.Phone -> {
            return true
        }

        // iOS has no nearby-Wi-Fi permission: joining a specific network goes through
        // NEHotspotConfiguration, which prompts on its own.
        Permission.NearbyWifiDevices -> {
            return true
        }
    }
}
