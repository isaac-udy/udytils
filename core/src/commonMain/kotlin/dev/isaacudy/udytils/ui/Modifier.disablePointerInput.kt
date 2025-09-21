package dev.isaacudy.udytils.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Disables pointer input on the composable and any children composables by collecting and consuming
 * pointer events during the initial pointer event pass when [disabled] is true.
 *
 * @param disabled Whether pointer input should be disabled. Defaults to true.
 * @return A [Modifier] that disables pointer input when [disabled] is true.
 */
fun Modifier.disablePointerInput(
    disabled: Boolean = true
): Modifier {
    if (!disabled) return this
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }
}