package dev.isaacudy.udytils.permissions

import android.Manifest
import android.os.Build

val Permission.androidPermission: String
    get() {
        return when (this) {
            Permission.Location.Approximate -> Manifest.permission.ACCESS_COARSE_LOCATION
            Permission.Location.Precise -> Manifest.permission.ACCESS_FINE_LOCATION
            Permission.Bluetooth.Scan -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN
            } else {
                Manifest.permission.BLUETOOTH
            }
            Permission.Bluetooth.Connect -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                Manifest.permission.BLUETOOTH
            }
            Permission.Phone -> Manifest.permission.READ_PHONE_STATE
            Permission.Camera -> Manifest.permission.CAMERA
        }
    }