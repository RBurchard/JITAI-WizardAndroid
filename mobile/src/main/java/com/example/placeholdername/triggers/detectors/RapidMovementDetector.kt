package com.BWPStudio.JITAIWizard.triggers.detectors

import com.example.jitaicompanion.convention.models.Trigger
import com.example.jitaicompanion.convention.models.TriggerKind
import com.example.jitaicompanion.convention.models.WatchDataSnapshot
import kotlin.math.sqrt

class RapidMovementDetector(private val trigger: Trigger) {
    private val params = trigger.kind as TriggerKind.RapidMovement
    private var lastFireTs = 0L

    fun onSnapshot(snap: WatchDataSnapshot): Boolean {
        val magnitude = sqrt(snap.accelX * snap.accelX + snap.accelY * snap.accelY + snap.accelZ * snap.accelZ)
        val gravity = 9.81f
        val deviation = kotlin.math.abs(magnitude - gravity)
        val now = snap.timestamp
        if (deviation >= params.accelThreshold) {
            if (now - lastFireTs >= params.windowMs) {
                lastFireTs = now
                return true
            }
        }
        return false
    }
}
