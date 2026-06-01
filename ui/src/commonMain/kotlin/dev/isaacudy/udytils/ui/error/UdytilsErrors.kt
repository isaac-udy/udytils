package dev.isaacudy.udytils.ui.error

import dev.enro.NavigationKey
import dev.isaacudy.udytils.error.getErrorMessage
import dev.isaacudy.udytils.ui.error.UdytilsErrors.addOnErrorListener

/**
 * Builds the [NavigationKey] opened when an [ErrorHandler] reports an error.
 *
 * Receives the raw [Throwable] (not a pre-built message) so application code
 * can branch on the error type — choosing a different destination, or building
 * a tailored [dev.isaacudy.udytils.error.ErrorMessage] — per error. The
 * [retryEnabled] flag reflects whether the originating handler was given an
 * `onRetry` action.
 */
typealias ErrorDestinationFactory = (error: Throwable, retryEnabled: Boolean) -> NavigationKey

object UdytilsErrors {
    private val onErrorListeners = mutableListOf<ErrorHandler.OnErrorListener>()

    /**
     * Zero-config default: if an app never installs its own factory (including
     * tests and previews), errors still surface via [ErrorDialogDestination].
     */
    private val defaultErrorDestinationFactory: ErrorDestinationFactory = { error, retryEnabled ->
        ErrorDialogDestination(
            errorMessage = error.getErrorMessage(),
            retryEnabled = retryEnabled,
        )
    }

    private var errorDestinationFactory: ErrorDestinationFactory = defaultErrorDestinationFactory

    /**
     * Installs the factory that turns a reported [Throwable] into the
     * [NavigationKey] to display. Call once at app startup, on the main thread,
     * before any error can be reported. If never called, errors fall back to
     * [ErrorDialogDestination].
     */
    fun setErrorDestinationFactory(factory: ErrorDestinationFactory) {
        errorDestinationFactory = factory
    }

    /** Restores the default factory. Intended for test teardown. */
    fun resetErrorDestinationFactory() {
        errorDestinationFactory = defaultErrorDestinationFactory
    }

    internal fun createErrorDestination(
        error: Throwable,
        retryEnabled: Boolean,
    ): NavigationKey = errorDestinationFactory(error, retryEnabled)

    internal fun onError(
        name: String,
        error: Throwable,
    ) {
        onErrorListeners.forEach { it.onError(name, error) }
    }

    /**
     * Adds a listener that will be invoked whenever an ErrorHandler is used to report an error
     */
    fun addOnErrorListener(listener: ErrorHandler.OnErrorListener) {
        onErrorListeners.add(listener)
    }

    /**
     * Removes a listener that was previously added to via [addOnErrorListener]
     */
    fun removeOnErrorListener(listener: ErrorHandler.OnErrorListener) {
        onErrorListeners.remove(listener)
    }
}
