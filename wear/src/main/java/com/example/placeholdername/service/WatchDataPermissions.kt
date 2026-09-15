package com.example.jitaicompanion.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * The one answer to "may [WatchDataService] run", shared by everything that starts it.
 *
 * The launcher activity and the phone message listener used to each check on their own, and
 * the listener checked `BODY_SENSORS`, which this app does not declare on Wear OS 4+ (it is
 * commented out of the manifest in favour of `READ_HEART_RATE`). Not declared means never
 * granted, so the listener's start path was dead on every current watch: a service that died,
 * or was never started because the app was not opened after a reboot, stayed down until
 * someone opened the app, however many runs the phone started in the meantime.
 */
object WatchDataPermissions {

    private const val TAG = "WatchDataPermissions"

    /** Health Connect's heart rate permission, the Wear OS 4+ replacement for BODY_SENSORS. */
    const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

    /** Needed for the step counter and for the `health` foreground service type. */
    val mandatory: Array<String> = arrayOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    /** Any one of these lets the heart rate sensor be read. */
    val heartRate: Array<String> = arrayOf(
        Manifest.permission.BODY_SENSORS,
        READ_HEART_RATE
    )

    fun canRunDataService(context: Context): Boolean {
        val missingMandatory = mandatory.filterNot { granted(context, it) }
        val hrGranted = heartRate.any { granted(context, it) }
        if (missingMandatory.isNotEmpty()) Log.w(TAG, "Missing mandatory: $missingMandatory")
        if (!hrGranted) Log.w(TAG, "No heart rate permission granted (BODY_SENSORS or READ_HEART_RATE)")
        return missingMandatory.isEmpty() && hrGranted
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
