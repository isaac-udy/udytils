package dev.isaacudy.udytils.permissions

import android.Manifest
import android.os.Build

val Permission.androidPermission: String?
    get() {
        return when (this) {
            Permission.Location.Approximate -> Manifest.permission.ACCESS_COARSE_LOCATION
            Permission.Location.Precise -> Manifest.permission.ACCESS_FINE_LOCATION
            Permission.Bluetooth.Scan -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Manifest.permission.BLUETOOTH_SCAN
                else -> null
            }
            Permission.Bluetooth.Connect -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Manifest.permission.BLUETOOTH_CONNECT
                else -> null
            }
            Permission.Phone -> Manifest.permission.READ_PHONE_STATE
            Permission.Camera -> Manifest.permission.CAMERA
            Permission.Notifications -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.POST_NOTIFICATIONS
                else -> null
            }
        }
    }