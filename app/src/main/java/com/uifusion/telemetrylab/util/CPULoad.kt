package com.uifusion.telemetrylab.util

object CPULoad {
    fun doWork(load: Int): Long {
        var acc = 0L
        val iterations = 20_000 * (1 shl load.coerceIn(0, 8))
        var x = 17L
        for (i in 0 until iterations) {
            x = x * 6364136223846793005L + 1442695040888963407L
            acc = acc xor x
        }
        return acc
    }
}
