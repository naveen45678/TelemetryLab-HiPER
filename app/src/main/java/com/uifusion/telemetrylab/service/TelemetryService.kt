package com.uifusion.telemetrylab.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.telemetrylab.util.CPULoad
import com.uifusion.telemetrylab.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean

class TelemetryService : Service() {

    companion object {
        const val CHANNEL_ID = "telemetry_chan"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "com.uifusion.telemetrylab.ACTION_STOP"
        const val EXTRA_LOAD = "com.uifusion.telemetrylab.EXTRA_LOAD"
        const val ACTION_METRICS = "com.uifusion.telemetrylab.ACTION_METRICS"
        const val EXTRA_JANK = "com.uifusion.telemetrylab.EXTRA_JANK"
        const val EXTRA_FPS = "com.uifusion.telemetrylab.EXTRA_FPS"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isRunning = AtomicBoolean(false)
    private var computeLoad = 2
    private var frameRateHz = 20L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        computeLoad = intent?.getIntExtra(EXTRA_LOAD, 2) ?: 2

        startForeground(NOTIFICATION_ID, buildNotification())

        if (isRunning.compareAndSet(false, true)) {
            scope.launch { loopFrames() }
        }

        return START_STICKY
    }

    private suspend fun loopFrames() {
        val intervalMs = 1000L / frameRateHz

        while (isRunning.get()) {
            val start = System.nanoTime()

            // Simulate CPU load
            val job = scope.async { CPULoad.doWork(computeLoad) }
            job.await()

            val durationNs = System.nanoTime() - start
            val elapsedMs = durationNs / 1_000_000L

            // Broadcast metrics
            val intent = Intent(ACTION_METRICS).apply {
                putExtra(EXTRA_JANK, 0.0) // Replace with actual Jank calculation if available
                putExtra(EXTRA_FPS, 1000.0 / elapsedMs) // Approx FPS
            }
            sendBroadcast(intent)

            val toSleep = intervalMs - elapsedMs
            if (toSleep > 0) delay(toSleep) else yield()
        }
    }

    override fun onDestroy() {
        isRunning.set(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TelemetryService::class.java).apply { action = ACTION_STOP }
        val pendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telemetry Lab running")
            .setContentText("Collecting telemetry data...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Telemetry Service", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Telemetry Lab foreground service" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
