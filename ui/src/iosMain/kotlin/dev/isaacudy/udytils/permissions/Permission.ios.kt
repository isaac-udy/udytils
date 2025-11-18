package dev.isaacudy.udytils.permissions

import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse

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
            val clLocationManager = CLLocationManager()

            val hasAuthorization = when (clLocationManager.authorizationStatus) {
                kCLAuthorizationStatusAuthorizedAlways,
                kCLAuthorizationStatusAuthorizedWhenInUse -> true

                else -> false
            }
            if (!hasAuthorization) return false

            val hasPrecise = when (clLocationManager.accuracyAuthorization) {
                CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy -> true
                else -> false
            }
            if (permission.requirePrecise && !hasPrecise) return false

            val hasBackground = when (clLocationManager.authorizationStatus) {
                kCLAuthorizationStatusAuthorizedAlways -> true
                else -> false
            }
            if (permission.requireBackground && !hasBackground) return false
            return true
        }

        Permission.Notifications -> {
            return true
        }

        Permission.Phone -> {
            return true
        }
    }
}