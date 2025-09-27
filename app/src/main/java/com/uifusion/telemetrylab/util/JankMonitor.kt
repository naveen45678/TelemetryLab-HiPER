package com.example.telemetrylab.util

import android.content.Context
import kotlin.math.max

class JankMonitor(private val context: Context, private val callback: (Double, Double) -> Unit) {
    private val frameTimesNs = ArrayDeque<Long>()

    fun recordFrame(durationNs: Long) {
        frameTimesNs.addLast(durationNs)
        if (frameTimesNs.size > 2000) frameTimesNs.removeFirst()
        if (frameTimesNs.size % 10 == 0) compute()
    }

    private fun compute() {
        if (frameTimesNs.isEmpty()) return
        val durationsMs = frameTimesNs.map { it / 1_000_000L }
        val targetMs = if (isBatterySaver()) 100.0 else 50.0
        val jankThreshold = targetMs * 1.5
        val janks = durationsMs.count { it > jankThreshold }
        val jankPercent = 100.0 * janks / durationsMs.size
        val totalMs = durationsMs.sum()
        val fps = if (totalMs > 0) (durationsMs.size.toDouble() / totalMs.toDouble()) * 1000.0 else 0.0
        callback(jankPercent, fps)
    }

    private fun isBatterySaver(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isPowerSaveMode
    }
}
