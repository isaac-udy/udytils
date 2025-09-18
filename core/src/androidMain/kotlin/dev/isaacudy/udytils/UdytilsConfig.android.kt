package dev.isaacudy.udytils

import android.app.Application
import dev.isaacudy.udytils.android.ActivityObserver

private var applicationReference: Application? = null

internal val UdytilsConfig.application: Application
    get() = requireNotNull(applicationReference) {
        "Udytils application is not initialized. Please call UdytilsConfig.install(application) in your Application class."
    }

fun UdytilsConfig.install(application: Application) {
    if (applicationReference != null) return
    application.registerActivityLifecycleCallbacks(ActivityObserver)
    applicationReference = application
}