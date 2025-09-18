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
internal fun hasIncorrectLocationPrecision(
    navigation: NavigationHandle<RequestPermissionDestination<Permission>>,
): Boolean {
    val permission = navigation.key.permission
    println("hasIncorrectLocationPrecision called w $permission")
    if (permission !is Permission.Location.Precise) return false
    val hasPrecisePermission = remember { hasPermission(permission) }
    val hasApproximatePermission = remember { hasPermission(Permission.Location.Approximate) }
    println("hasIncorrectLocationPrecision called aaaaand: hasApproximatePermission: $hasApproximatePermission, hasPrecisePermission: $hasPrecisePermission,")
    return hasApproximatePermission && !hasPrecisePermission
}

@Composable
internal fun IncorrectLocationPrecisionContent(
    navigation: NavigationHandle<RequestPermissionDestination<Permission>>,
) {
    require(navigation.key.permission is Permission.Location.Precise) {
        "IncorrectLocationPrecisionContent can only be used with Permission.Location.Precise"
    }
    val permission = navigation.key.permission
    fun onResult() {
        if (!hasPermission(permission)) return
        navigation.complete(PermissionStatus.Granted(navigation.key.permission))
    }
    val applicationSettingsResult = registerForNavigationResult(
        onClosed = { onResult() },
        onCompleted = { onResult() },
    )
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
