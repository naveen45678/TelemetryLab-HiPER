package com.uifusion.telemetrylab.util

import android.content.Context
import android.content.Intent
import android.os.Build

fun Context.startForegroundServiceCompat(intent: Intent) {
    if (Build.VERSION.SDK_INT >= 26) this.startForegroundService(intent) else this.startService(intent)
}