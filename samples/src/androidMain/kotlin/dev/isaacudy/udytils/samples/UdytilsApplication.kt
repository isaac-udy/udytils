package dev.isaacudy.udytils.samples

import android.app.Application
import dev.isaacudy.udytils.android.Udytils

class UdytilsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Udytils.install(this)
        SamplesNavigation.installNavigationController(this)
    }
}
