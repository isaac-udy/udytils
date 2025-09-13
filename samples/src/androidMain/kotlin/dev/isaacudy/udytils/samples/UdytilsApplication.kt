package dev.isaacudy.udytils.samples

import android.app.Application

class UdytilsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SamplesNavigation.installNavigationController(this)
    }
}