package dev.isaacudy.udytils.permissions

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import dev.enro.NavigationHandle
import dev.enro.annotations.NavigationDestination
import dev.enro.complete
import dev.enro.requestClose
import dev.enro.result.open
import dev.enro.result.registerForNavigationResult
import dev.enro.ui.navigationDestination
import dev.enro.ui.scenes.directOverlay
import dev.isaacudy.udytils.android.ApplicationSettingsDestination
import org.jetbrains.compose.resources.stringResource

@NavigationDestination(RequestPermissionDestination::class)
val requestPermissionDestination = navigationDestination<RequestPermissionDestination<Permission>>(
    metadata = { directOverlay() }
) {
    val activity = requireNotNull(LocalActivity.current)
    val permanentlyDeniedState = remember { PermanentlyDeniedState(activity) }
    val initialStatus = remember {
        val hasPermission = hasPermission(navigation.key.permission)
        val isPermanentlyDenied =
            permanentlyDeniedState.isPermanentlyDenied(navigation.key.permission)
        when {
            hasPermission -> PermissionStatus.Granted(navigation.key.permission)
            isPermanentlyDenied -> PermissionStatus.DeniedPermanently(navigation.key.permission)
            else -> PermissionStatus.Denied(navigation.key.permission)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val result = when {
            isGranted -> PermissionStatus.Granted(navigation.key.permission)
            else -> {
                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, navigation.key.permission.androidPermission
                )
                when {
                    shouldShowRationale -> PermissionStatus.Denied(navigation.key.permission)
                    else -> PermissionStatus.DeniedPermanently(navigation.key.permission)
                }
            }
        }
        permanentlyDeniedState.setPermanentlyDenied(
            permission = navigation.key.permission,
            isPermanentlyDenied = result is PermissionStatus.DeniedPermanently
        )
        navigation.complete(result)
    }

    val hasIncorrectLocationPermission = hasIncorrectLocationPrecision(navigation)
    if (hasIncorrectLocationPermission) {
        IncorrectLocationPrecisionContent(navigation)
        return@navigationDestination
    }

    when (initialStatus) {
        is PermissionStatus.Granted -> LaunchedEffect(Unit) {
            navigation.complete(initialStatus)
        }

        is PermissionStatus.Denied -> LaunchedEffect(Unit) {
            launcher.launch(navigation.key.permission.androidPermission)
        }

        is PermissionStatus.DeniedPermanently -> {
            PermanentlyDeniedContent(navigation)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermanentlyDeniedContent(
    navigation: NavigationHandle<RequestPermissionDestination<Permission>>
) {
    fun onResult() {
        if (!hasPermission(navigation.key.permission)) return
        navigation.complete(PermissionStatus.Granted(navigation.key.permission))
    }

    val applicationSettingsResult = registerForNavigationResult(
        onClosed = { onResult() },
        onCompleted = { onResult() },
    )

    val permissionName = stringResource(navigation.key.permission.name)
    AlertDialog(
        onDismissRequest = { navigation.requestClose() },
        title = {
            Text("Enable $permissionName")
        },
        text = {
            Text("To enable the $permissionName permission you must manually enable it in the application settings.")
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

private class PermanentlyDeniedState(
    context: Context,
) {
    private val sharedPreferences = context.applicationContext.getSharedPreferences(
        "dev.isaacudy.udytils.permissions.PermanentlyDeniedState",
        Context.MODE_PRIVATE,
    )

    fun isPermanentlyDenied(permission: Permission): Boolean {
        return sharedPreferences.contains(getPreferenceName(permission))
    }

    fun setPermanentlyDenied(permission: Permission, isPermanentlyDenied: Boolean) {
        sharedPreferences.edit {
            if (isPermanentlyDenied) {
                putBoolean(getPreferenceName(permission), true)
            } else {
                remove(getPreferenceName(permission))
            }
        }
    }

    /**
     * The "permanently denied" state of some permissions are tied together, so
     * we need to make sure that these permissions share a name in the shared preferences.
     */
    private fun getPreferenceName(permission: Permission): String {
        return when (permission) {
            is Permission.Location.Approximate,
            is Permission.Location.Precise -> {
                Permission.Location::class.java.name
            }
            else -> permission::class.java.name
        }
    }
}
