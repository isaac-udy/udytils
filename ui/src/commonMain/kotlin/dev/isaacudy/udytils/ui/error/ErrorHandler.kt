package dev.isaacudy.udytils.ui.error

import androidx.lifecycle.ViewModel
import dev.enro.result.NavigationResultChannel
import dev.enro.result.open
import dev.enro.result.registerForNavigationResult
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

class ErrorHandler(
    private val name: String,
    private val onRetry: (() -> Unit)?,
    private val resultChannel: NavigationResultChannel<Unit>,
) {
    /**
     * Guards against stacking multiple error dialogs when a flow re-emits
     * errors (e.g. a retried request that keeps failing). Set when a dialog
     * opens, cleared when it resolves (retry or dismiss) via [onDialogResolved].
     */
    private var isShowing = false

    fun onError(error: Throwable) {
        // Always notify global listeners (logging / analytics), even if a
        // dialog is already on screen.
        UdytilsErrors.onError(name, error)
        if (isShowing) return
        isShowing = true
        resultChannel.open(
            UdytilsErrors.createErrorDestination(
                error = error,
                retryEnabled = onRetry != null,
            )
        )
    }

    internal fun onDialogResolved() {
        isShowing = false
    }

    fun interface OnErrorListener {
        fun onError(
            name: String,
            error: Throwable
        )
    }
}

/**
 * Registers an [ErrorHandler] bound to this ViewModel's navigation handle.
 * Route async failures through [ErrorHandler.onError] to surface them as the
 * app's configured error destination (see [UdytilsErrors.setErrorDestinationFactory]).
 *
 * @param onRetry invoked when the user chooses "Retry" (the dialog completes).
 *   When non-null, the dialog offers a retry affordance.
 * @param onDismiss invoked when the user dismisses the dialog without retrying.
 */
fun ViewModel.registerErrorHandler(
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
): PropertyDelegateProvider<ViewModel, ReadOnlyProperty<ViewModel, ErrorHandler>> {
    return PropertyDelegateProvider { thisRef, property ->
        lateinit var errorHandler: ErrorHandler
        val channel = thisRef
            .registerForNavigationResult(
                onClosed = {
                    errorHandler.onDialogResolved()
                    onDismiss?.invoke()
                },
                onCompleted = {
                    errorHandler.onDialogResolved()
                    onRetry?.invoke()
                },
            )
            .provideDelegate(thisRef, property)
            .getValue(thisRef, property)
        errorHandler = ErrorHandler(
            name = "${thisRef::class.simpleName}.${property.name}",
            onRetry = onRetry,
            resultChannel = channel,
        )

        return@PropertyDelegateProvider ReadOnlyProperty { _, _ -> errorHandler }
    }
}
