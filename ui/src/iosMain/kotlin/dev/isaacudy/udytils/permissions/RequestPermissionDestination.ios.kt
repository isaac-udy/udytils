package dev.isaacudy.udytils.permissions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import dev.enro.annotations.NavigationDestination
import dev.enro.complete
import dev.enro.requestClose
import dev.enro.ui.navigationDestination
import dev.enro.ui.scenes.directOverlay
import org.jetbrains.compose.resources.stringResource
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/** Keeps a usable Settings path visible even when iOS refuses to show another system prompt. */
@NavigationDestination(RequestPermissionDestination::class)
val requestPermissionDestination = navigationDestination<RequestPermissionDestination<Permission>>(
    metadata = { directOverlay() },
) {
    val permission = navigation.key.permission
    val granted = rememberHasPermission(permission)
    LaunchedEffect(granted) {
        if (granted) navigation.complete(PermissionStatus.Granted(permission))
    }
    LaunchedEffect(permission) {
        if (!hasPermission(permission)) requestNativePermission(permission)
    }
    if (granted) return@navigationDestination

    val permissionName = stringResource(permission.name)
    AlertDialog(
        onDismissRequest = { navigation.requestClose() },
        title = { Text("Enable $permissionName") },
        text = {
            Text(when {
                permission is Permission.Location && permission.requireBackground ->
                    "Choose Always for Location in Settings to allow location access in the background." +
                        if (permission.requirePrecise) " Turn on Precise Location too." else ""
                permission is Permission.Location && permission.requirePrecise ->
                    "Allow Location access and turn on Precise Location in Settings."
                else -> "Allow $permissionName in Settings to continue."
            })
        },
        confirmButton = {
            TextButton(onClick = {
                NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
                    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
                }
            }) { Text("Open Settings") }
        },
        dismissButton = {
            TextButton(onClick = { navigation.requestClose() }) { Text("Not now") }
        },
    )
}

private fun requestNativePermission(permission: Permission) {
    when (permission) {
        is Permission.Location -> IosPermissionObserver.requestLocation(permission)
        Permission.Camera,
        Permission.Microphone -> {
            val mediaType = if (permission == Permission.Camera) AVMediaTypeVideo else AVMediaTypeAudio
            if (AVCaptureDevice.authorizationStatusForMediaType(mediaType) == AVAuthorizationStatusNotDetermined) {
                AVCaptureDevice.requestAccessForMediaType(mediaType) { IosPermissionObserver.changed() }
            }
        }
        Permission.Bluetooth.Connect,
        Permission.Bluetooth.Scan -> {
            if (CBManager.authorization == CBManagerAuthorizationNotDetermined) {
                IosPermissionObserver.requestBluetooth()
            }
        }
        Permission.Notifications,
        Permission.Phone,
        Permission.NearbyWifiDevices -> Unit
    }
}
