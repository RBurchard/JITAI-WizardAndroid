package com.example.jitaicompanion

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.jitaicompanion.power.WatchPowerPolicy

/**
 * Exists to tell [WatchPowerPolicy] when a screen of this app is actually in front of someone.
 *
 * Registering the callbacks here rather than in each activity means the games, the intervention
 * screen and the settings screen are all counted without any of them knowing about power
 * profiles, and a screen that is added later is counted too.
 */
class WatchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = WatchPowerPolicy.onActivityResumed()
            override fun onActivityPaused(activity: Activity) = WatchPowerPolicy.onActivityPaused()

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
