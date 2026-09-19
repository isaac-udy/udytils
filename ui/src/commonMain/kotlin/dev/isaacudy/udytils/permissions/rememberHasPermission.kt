package dev.isaacudy.udytils.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.Flow

/**
 * Observes native permission callbacks and lifecycle changes, including returning from Settings.
 *
 * Backed by [hasPermission]: works on Android and iOS; on desktop JVM and wasmJs it currently
 * throws [NotImplementedError] because those `actual`s are not yet implemented.
 */
@Composable
fun rememberHasPermission(permission: Permission): Boolean {
    val permissionState = remember(permission) {
        mutableStateOf(hasPermission(permission))
    }
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState().value
    LaunchedEffect(permission, lifecycleState) {
        permissionState.value = hasPermission(permission)
        permissionChanges().collect {
            permissionState.value = hasPermission(permission)
        }
    }
    return permissionState.value
}

// Some iOS prompts change authorization without changing the Compose lifecycle state.
internal expect fun permissionChanges(): Flow<Unit>
