package com.BWPStudio.JITAIWizard.triggers.detectors

import com.example.jitaicompanion.convention.models.Trigger
import com.example.jitaicompanion.convention.models.TriggerKind
import com.example.jitaicompanion.convention.models.WatchDataSnapshot
import kotlin.math.sqrt

class RepeatingMovementDetector(trigger: Trigger) {
    private val params = trigger.kind as TriggerKind.RepeatingMovement
    private val windowSec = 5f
    private data class Sample(val ts: Long, val mag: Float)
    private val window = ArrayDeque<Sample>()
    private var conditionStartedAt: Long = 0L
    private var firedForCurrentSpell = false
    private var lastEvalTs: Long = 0L

    fun onSnapshot(snap: WatchDataSnapshot): Boolean {
        val mag = sqrt(snap.accelX * snap.accelX + snap.accelY * snap.accelY + snap.accelZ * snap.accelZ) - 9.81f
        window.addLast(Sample(snap.timestamp, mag))
        val cutoff = snap.timestamp - (windowSec * 1000).toLong()
        while (window.isNotEmpty() && window.first().ts < cutoff) window.removeFirst()

        if (snap.timestamp - lastEvalTs < 200) return false
        lastEvalTs = snap.timestamp
        if (window.size < 16) return false

        val peakHz = dominantFrequencyHz()
        val inBand = peakHz in params.minHz..params.maxHz
        if (!inBand) {
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

    private fun dominantFrequencyHz(): Float {
        val n = window.size
        val durationMs = (window.last().ts - window.first().ts).coerceAtLeast(1)
        val sampleRateHz = (n - 1) * 1000f / durationMs
        if (sampleRateHz <= 0f) return 0f
        val mean = window.sumOf { it.mag.toDouble() }.toFloat() / n
        val centered = FloatArray(n) { window.elementAt(it).mag - mean }
        val maxLag = (sampleRateHz / params.minHz).toInt().coerceIn(2, n - 1)
        val minLag = (sampleRateHz / params.maxHz).toInt().coerceAtLeast(1)
        var bestLag = 0
        var bestVal = 0f
        for (lag in minLag..maxLag) {
            var acc = 0f
            for (i in 0 until (n - lag)) acc += centered[i] * centered[i + lag]
            if (acc > bestVal) { bestVal = acc; bestLag = lag }
        }
        if (bestLag == 0) return 0f
        val variance = centered.sumOf { (it * it).toDouble() }.toFloat()
        if (variance <= 0f || bestVal / variance < 0.3f) return 0f
        return sampleRateHz / bestLag
    }
}
