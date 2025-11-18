package dev.isaacudy.udytils.permissions

import dev.enro.annotations.NavigationDestination
import dev.enro.ui.destinations.syntheticDestination
import platform.CoreLocation.CLLocationManager

@NavigationDestination(RequestPermissionDestination::class)
val requestPermissionDestination = syntheticDestination<RequestPermissionDestination<Permission>> {
    when(key.permission) {
        is Permission.Location -> {
            println("Requesting location permission")
            CLLocationManager().requestAlwaysAuthorization()
        }
        Permission.Bluetooth.Connect -> TODO()
        Permission.Bluetooth.Scan -> TODO()
        Permission.Camera -> TODO()
        Permission.Notifications -> TODO()
        Permission.Phone -> TODO()
    }
}