package dev.isaacudy.udytils.permissions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal actual fun permissionChanges(): Flow<Unit> = emptyFlow()
