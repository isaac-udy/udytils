package dev.isaacudy.udytils.error

import androidx.lifecycle.ViewModel
import dev.enro.result.NavigationResultChannel
import dev.enro.result.open
import dev.enro.result.registerForNavigationResult
import dev.isaacudy.udytils.error.ui.ErrorDialogDestination
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

class ErrorHandler(
    private val onRetry: (() -> Unit)?,
    private val resultChannel: NavigationResultChannel<Unit>,
) {
    fun onError(error: Throwable) {
        resultChannel.open(
            ErrorDialogDestination(error.getErrorMessage())
        )
    }
}

fun ViewModel.registerErrorHandler(
    onRetry: (() -> Unit)?
): PropertyDelegateProvider<ViewModel, ReadOnlyProperty<ViewModel, ErrorHandler>> {
    return PropertyDelegateProvider { thisRef, property ->
        val channel = thisRef
            .registerForNavigationResult {
                onRetry?.invoke()
            }
            .provideDelegate(thisRef, property)
            .getValue(thisRef, property)
        val errorHandler = ErrorHandler(onRetry, channel)

        return@PropertyDelegateProvider ReadOnlyProperty { _, _ -> errorHandler }
    }
}