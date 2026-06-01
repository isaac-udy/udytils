package dev.isaacudy.udytils.ui.error

import androidx.lifecycle.ViewModel
import dev.enro.NavigationKey
import dev.enro.annotations.AdvancedEnroApi
import dev.enro.result.NavigationResultChannel
import dev.enro.result.open
import dev.enro.result.registerForNavigationResult
import dev.enro.withMetadata
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * Transient metadata carrying the retry args that were passed to
 * [ErrorHandler.onError]. The args are attached to the error destination when
 * it is opened and read back when the dialog completes, so the retry can be
 * invoked with the same args without the caller stashing them in a field.
 *
 * Transient => not persisted across saved instance state, so the args may be
 * any type (including non-serializable domain objects). A retry that would
 * have spanned process death is moot anyway: the dialog itself is gone.
 */
@OptIn(AdvancedEnroApi::class)
internal object RetryArgsKey : NavigationKey.TransientMetadataKey<Any?>(default = null)

/**
 * Surfaces async failures as the app's configured error destination (see
 * [UdytilsErrors.setErrorDestinationFactory]). Obtain one via
 * [registerErrorHandler]; route failures through [onError].
 *
 * [T] is the type of the retry args — the value passed to [onError] that is
 * handed back to the `onRetry` lambda when the user retries. Use [Unit] (via
 * the no-arg [registerErrorHandler] overload) when retry needs no arguments.
 */
class ErrorHandler<T> internal constructor(
    private val name: String,
    private val retryEnabled: Boolean,
    private val resultChannel: NavigationResultChannel<Unit>,
) {
    /**
     * Guards against stacking multiple error dialogs when a flow re-emits
     * errors (e.g. a retried request that keeps failing). Set when a dialog
     * opens, cleared when it resolves (retry or dismiss) via [onDialogResolved].
     */
    private var isShowing = false

    fun onError(error: Throwable, args: T) {
        // Always notify global listeners (logging / analytics), even if a
        // dialog is already on screen.
        UdytilsErrors.onError(name, error)
        if (isShowing) return
        isShowing = true
        val destination = UdytilsErrors.createErrorDestination(
            error = error,
            retryEnabled = retryEnabled,
        )
        // Attach the retry args to the opened destination so they ride along
        // and come back via the result scope when the dialog completes.
        resultChannel.open(destination.withMetadata(RetryArgsKey, args))
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

/** Convenience for handlers whose retry needs no arguments. */
fun ErrorHandler<Unit>.onError(error: Throwable): Unit = onError(error, Unit)

/**
 * Registers a typed [ErrorHandler] bound to this ViewModel's navigation handle.
 * Route async failures through [ErrorHandler.onError], passing the args the
 * retry needs — those args ride along as transient navigation metadata on the
 * error destination and are delivered back to [onRetry] when the user retries.
 * This removes the need to stash retry arguments in ViewModel fields.
 *
 * @param onRetry invoked with the args from [ErrorHandler.onError] when the
 *   user chooses "Retry". When non-null, the dialog offers a retry affordance.
 * @param onDismiss invoked when the user dismisses the dialog without retrying.
 */
fun <T> ViewModel.registerErrorHandler(
    onRetry: ((T) -> Unit)?,
    onDismiss: (() -> Unit)? = null,
): PropertyDelegateProvider<ViewModel, ReadOnlyProperty<ViewModel, ErrorHandler<T>>> {
    return PropertyDelegateProvider { thisRef, property ->
        lateinit var errorHandler: ErrorHandler<T>
        val channel = thisRef
            .registerForNavigationResult(
                onClosed = {
                    errorHandler.onDialogResolved()
                    onDismiss?.invoke()
                },
                onCompleted = {
                    errorHandler.onDialogResolved()
                    @Suppress("UNCHECKED_CAST")
                    val args = instance.metadata.get(RetryArgsKey) as T
                    onRetry?.invoke(args)
                },
            )
            .provideDelegate(thisRef, property)
            .getValue(thisRef, property)
        errorHandler = ErrorHandler(
            name = "${thisRef::class.simpleName}.${property.name}",
            retryEnabled = onRetry != null,
            resultChannel = channel,
        )

        return@PropertyDelegateProvider ReadOnlyProperty { _, _ -> errorHandler }
    }
}

/**
 * Registers an [ErrorHandler] whose retry takes no arguments. Use the typed
 * overload when the retry needs the data that triggered the failed action.
 */
fun ViewModel.registerErrorHandler(
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
): PropertyDelegateProvider<ViewModel, ReadOnlyProperty<ViewModel, ErrorHandler<Unit>>> {
    return registerErrorHandler<Unit>(
        onRetry = onRetry?.let { retry -> { _: Unit -> retry() } },
        onDismiss = onDismiss,
    )
}
