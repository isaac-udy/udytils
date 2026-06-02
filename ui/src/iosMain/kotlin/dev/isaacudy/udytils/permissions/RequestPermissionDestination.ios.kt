package dev.isaacudy.udytils.permissions

import dev.enro.annotations.NavigationDestination
import dev.enro.ui.destinations.syntheticDestination
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLLocationManager

/**
 * Triggers the iOS system permission prompt for the requested [Permission]. The synthetic
 * destination doesn't propagate a result back through Enro on this platform — the parent screen
 * polls [hasPermission] (via [rememberHasPermission]) and recomposes when the user makes a choice.
 */
@NavigationDestination(RequestPermissionDestination::class)
val requestPermissionDestination = syntheticDestination<RequestPermissionDestination<Permission>> {
    when(key.permission) {
        is Permission.Location -> {
            CLLocationManager().requestAlwaysAuthorization()
        }
        Permission.Camera -> {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ -> }
        }
        Permission.Microphone -> {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { _ -> }
        }
        // Bluetooth, Notifications, and Phone aren't yet routed through this destination on iOS.
        Permission.Bluetooth.Connect,
        Permission.Bluetooth.Scan,
        Permission.Notifications,
        Permission.Phone -> Unit
    }
}