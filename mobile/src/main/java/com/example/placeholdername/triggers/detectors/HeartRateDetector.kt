package com.BWPStudio.JITAIWizard.triggers.detectors

import com.example.jitaicompanion.convention.models.Trigger
import com.example.jitaicompanion.convention.models.TriggerKind
import com.example.jitaicompanion.convention.models.WatchDataSnapshot

class HeartRateDetector(trigger: Trigger) {
    private val params = trigger.kind as TriggerKind.HeartRate
    private var conditionStartedAt: Long = 0L
    private var firedForCurrentSpell = false

    fun onSnapshot(snap: WatchDataSnapshot): Boolean {
        if (snap.heartRate <= 0f) return false
        val matches = if (params.above) snap.heartRate >= params.bpm else snap.heartRate <= params.bpm
        if (!matches) {
            conditionStartedAt = 0L
            firedForCurrentSpell = false
            return false
        }
        if (conditionStartedAt == 0L) {
            conditionStartedAt = snap.timestamp
            return false
        }
        val sustainMs = (params.sustainSec * 1000).toLong()
        if (!firedForCurrentSpell && snap.timestamp - conditionStartedAt >= sustainMs) {
            firedForCurrentSpell = true
            return true
        }
        return false
    }
}
