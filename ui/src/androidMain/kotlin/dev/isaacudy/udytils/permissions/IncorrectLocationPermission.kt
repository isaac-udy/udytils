package dev.isaacudy.udytils.permissions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.enro.NavigationHandle
import dev.enro.complete
import dev.enro.requestClose
import dev.enro.result.open
import dev.enro.result.registerForNavigationResult
import dev.isaacudy.udytils.android.ApplicationSettingsDestination

@Composable
internal fun hasIncorrectLocationPermission(
    navigation: NavigationHandle<RequestPermissionDestination<Permission>>,
): Boolean {
    val permission = navigation.key.permission
    if (permission !is Permission.Location) return false

    val hasPrecisePermission = remember {
        permission.hasPreciseLocation()
    }
    val hasApproximatePermission = remember {
        permission.hasApproximateLocation()
    }
    val hasBackgroundPermission = remember {
        permission.hasBackgroundLocation()
    }
    if (permission.requirePrecise && !hasPrecisePermission && hasApproximatePermission) return true
    if (permission.requireBackground && !hasBackgroundPermission) return true
    return false
}

@Composable
internal fun IncorrectLocationPermissionContent(
    navigation: NavigationHandle<RequestPermissionDestination<Permission>>,
) {
    val permission = navigation.key.permission
    require(permission is Permission.Location) {
        "IncorrectLocationPrecisionContent can only be used with Permission.Location"
    }
    fun onResult() {
        if (!hasPermission(permission)) return
        navigation.complete(PermissionStatus.Granted(navigation.key.permission))
    }

    val hasPrecisePermission = remember {
        permission.hasPreciseLocation()
    }

    val hasBackgroundPermission = remember {
        permission.hasBackgroundLocation()
    }

    val applicationSettingsResult = registerForNavigationResult(
        onClosed = { onResult() },
        onCompleted = { onResult() },
    )

    if (permission.requirePrecise && !hasPrecisePermission) {
        AlertDialog(
            onDismissRequest = { navigation.requestClose() },
            title = {
                Text("Location Precision")
            },
            text = {
                Text("You have selected to allow only approximate location access. This application requires precise location access to function correctly. Please enable precise location access in the application settings.")
            },
            confirmButton = {
                TextButton(
                    onClick = { applicationSettingsResult.open(ApplicationSettingsDestination) }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { navigation.requestClose() }
                ) {
                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.66f
                        ),
                    )
                }
            },
        )
    }

    if (permission.requireBackground && !hasBackgroundPermission) {
        AlertDialog(
            onDismissRequest = { navigation.requestClose() },
            title = {
                Text("Background Location")
            },
            text = {
                Text("You have selected to allow only location access while using the app. This application requires location access while the app is in the background to function correctly. Please enable background location access in the application settings.")
            },
            confirmButton = {
                TextButton(
                    onClick = { applicationSettingsResult.open(ApplicationSettingsDestination) }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { navigation.requestClose() }
                ) {
                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.66f
                        ),
                    )
                }
            },
        )
    }
}
