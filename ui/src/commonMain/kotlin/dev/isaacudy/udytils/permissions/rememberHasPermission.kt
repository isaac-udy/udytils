package dev.isaacudy.udytils.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Observes whether [permission] is granted, re-checking on every lifecycle state change — so the
 * value refreshes when the user returns from a system permission dialog or the settings app.
 *
 * Backed by [hasPermission]: works on Android and iOS; on desktop JVM and wasmJs it currently
 * throws [NotImplementedError] because those `actual`s are not yet implemented.
 */
@Composable
fun rememberHasPermission(permission: Permission): Boolean {
    val permissionState = remember {
        mutableStateOf(hasPermission(permission))
    }
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState().value
    LaunchedEffect(lifecycleState) {
        permissionState.value = hasPermission(permission)
    }
    return permissionState.value
}
