package dev.isaacudy.udytils.ui.error

import dev.isaacudy.udytils.ui.error.UdytilsErrors.addOnErrorListener


object UdytilsErrors {
    private val onErrorListeners = mutableListOf<ErrorHandler.OnErrorListener>()

    internal fun onError(error: Throwable) {
        onErrorListeners.forEach { it.onError(error) }
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