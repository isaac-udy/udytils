package dev.isaacudy.udytils.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import java.lang.ref.WeakReference

internal object ActivityObserver : Application.ActivityLifecycleCallbacks {

    private val activities = mutableMapOf<Int, WeakReference<Activity>>()

    private fun register(activity: Activity) {
        activities[activity.hashCode()] = WeakReference(activity)
        activities
            .mapNotNull { (id, ref) ->
                val activity = ref.get()
                if (activity == null || shouldBeRemoved(activity)) return@mapNotNull id
                return@mapNotNull null
            }
            .forEach { id ->
                activities.remove(id)
            }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
    ) {
        register(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        register(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        register(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        register(activity)
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle
    ) {
        register(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        register(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        register(activity)
    }

    private fun shouldBeRemoved(activity: Activity): Boolean {
        if (activity.isDestroyed || activity.isFinishing) {
            return true
        }
        if (activity !is ComponentActivity) return false
        return activity.lifecycle.currentState < Lifecycle.State.CREATED
    }
}