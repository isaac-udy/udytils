package dev.isaacudy.udytils.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner

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
