package dev.isaacudy.udytils.android

import android.app.Application

object Udytils {
    private var applicationReference: Application? = null

    internal val application: Application
        get() = requireNotNull(applicationReference) {
            "Udytils application is not initialized. Please call UdytilsConfig.install(application) in your Application class."
        }

    fun install(application: Application) {
        if (applicationReference != null) return
        application.registerActivityLifecycleCallbacks(ActivityObserver)
        applicationReference = application
    }
}
