package dev.isaacudy.udytils.permissions

import android.Manifest
import android.os.Build

val Permission.androidManifestPermissions: List<String>
    get() {
        val permission = this
        return when (permission) {
            is Permission.Location -> listOfNotNull(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION.takeIf { permission.requirePrecise },
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    else -> null
                }.takeIf { permission.requireBackground }
            )
            Permission.Bluetooth.Scan -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
                    Manifest.permission.BLUETOOTH_SCAN
                )
                else -> emptyList()
            }
            Permission.Bluetooth.Connect -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
                    Manifest.permission.BLUETOOTH_CONNECT
                )
                else -> emptyList()
            }
            Permission.Phone -> listOf(
                Manifest.permission.READ_PHONE_STATE
            )
            Permission.Camera -> listOf(
                Manifest.permission.CAMERA
            )
            Permission.Notifications -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                    Manifest.permission.POST_NOTIFICATIONS
                )
                else -> emptyList()
            }
        }
    }