package com.uifusion.telemetrylab.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.uifusion.telemetrylab.R
import com.uifusion.telemetrylab.util.CPULoad
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class TelemetryService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isRunning = AtomicBoolean(false)

    private val computeLoad = AtomicInteger(2)

    private val frameTimes = mutableListOf<Pair<Long, Long>>()
    private val maxAgeMs = 30_000L

    private lateinit var powerManager: PowerManager
    @Volatile private var isBatterySaverOn = false

    private lateinit var batteryReceiver: BroadcastReceiver
    private lateinit var loadUpdateReceiver: BroadcastReceiver

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        updateBatterySaverState()

        android.util.Log.d("TelemetryService", "Service created - Initial battery saver: $isBatterySaverOn")

        // Battery Saver receiver
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                android.util.Log.d("TelemetryService", "Battery broadcast received")
                updateBatterySaverState()
            }
        }

        loadUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val newLoad = intent?.getIntExtra(EXTRA_LOAD, 2) ?: 2
                computeLoad.set(newLoad)
                android.util.Log.d("TelemetryService", "Load updated to: $newLoad")

                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, buildNotification())
            }
        }

        val batteryFilter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(batteryReceiver, batteryFilter)
        }

        val loadFilter = IntentFilter(ACTION_UPDATE_LOAD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(loadUpdateReceiver, loadFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(loadUpdateReceiver, loadFilter)
        }
    }

    private fun updateBatterySaverState() {
        val oldState = isBatterySaverOn
        val newState1 = powerManager.isPowerSaveMode
        val newState2 = try {
            android.provider.Settings.Global.getInt(contentResolver, "low_power", 0) == 1
        } catch (e: Exception) {
            false
        }

        isBatterySaverOn = newState1 || newState2

        if (oldState != isBatterySaverOn) {
            android.util.Log.d("TelemetryService", "Battery saver changed: $oldState -> $isBatterySaverOn")

            val statusIntent = Intent(ACTION_BATTERY_STATUS).apply {
                putExtra(EXTRA_BATTERY_SAVER, isBatterySaverOn)
            }
            sendBroadcast(statusIntent)

            if (isRunning.get()) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_LOAD -> {
                val newLoad = intent.getIntExtra(EXTRA_LOAD, 2)
                computeLoad.set(newLoad)
                android.util.Log.d("TelemetryService", "Load updated via start command: $newLoad")
                return START_STICKY
            }
        }

        computeLoad.set(intent?.getIntExtra(EXTRA_LOAD, 2) ?: 2)

        startForeground(NOTIFICATION_ID, buildNotification())

        if (isRunning.compareAndSet(false, true)) {
            scope.launch { loopFrames() }
        }

        return START_STICKY
    }

    private suspend fun loopFrames() {
        var frameCount = 0L
        while (isRunning.get()) {
            frameCount++

            if (frameCount % 20 == 0L) {
                updateBatterySaverState()
            }

            val batterySaverOn = isBatterySaverOn
            val currentLoad = computeLoad.get()
            val currentFrameRateHz = if (batterySaverOn) 10L else 20L
            val currentComputeLoad = if (batterySaverOn) max(0, currentLoad - 1) else currentLoad

            if (frameCount % 100 == 0L) {
                android.util.Log.d(
                    "TelemetryService",
                    "Frame $frameCount: BatterySaver=$batterySaverOn, Rate=${currentFrameRateHz}Hz, Load=$currentComputeLoad"
                )
            }

            val intervalMs = 1000L / currentFrameRateHz
            val frameStart = System.currentTimeMillis()
            val workStart = System.nanoTime()

            val job = scope.async(Dispatchers.Default) {
                CPULoad.doWork(currentComputeLoad)
            }
            job.await()

            val workDurationMs = (System.nanoTime() - workStart) / 1_000_000L

            synchronized(frameTimes) {
                frameTimes.add(frameStart to workDurationMs)

                val cutoffTime = frameStart - maxAgeMs
                frameTimes.removeAll { it.first < cutoffTime }
            }

            if (frameCount % 5 == 0L) {
                val (jankPercent, fps) = calculateMetrics(currentFrameRateHz)

                val metricsIntent = Intent(ACTION_METRICS).apply {
                    putExtra(EXTRA_JANK, jankPercent)
                    putExtra(EXTRA_FPS, if (batterySaverOn) 0.0 else fps)
                    putExtra(EXTRA_CURRENT_LOAD, currentLoad)
                    putExtra(EXTRA_EFFECTIVE_LOAD, currentComputeLoad)
                    putExtra(EXTRA_BATTERY_SAVER, batterySaverOn)
                }
                sendBroadcast(metricsIntent)
            }

            val toSleep = intervalMs - workDurationMs
            if (toSleep > 0) delay(toSleep) else yield()
        }
    }

    private fun calculateMetrics(targetFrameRate: Long): Pair<Double, Double> {
        synchronized(frameTimes) {
            if (frameTimes.isEmpty()) return 0.0 to 0.0

            val targetFrameTimeMs = 1000.0 / targetFrameRate
            val jankThresholdMs = targetFrameTimeMs * 1.5

            val durations = frameTimes.map { it.second }

            val jankCount = durations.count { it > jankThresholdMs }
            val jankPercent = (jankCount.toDouble() / durations.size) * 100.0

            val fps = if (frameTimes.size > 1) {
                val timeSpanMs = frameTimes.last().first - frameTimes.first().first
                if (timeSpanMs > 0) {
                    (frameTimes.size - 1).toDouble() / (timeSpanMs / 1000.0)
                } else 0.0
            } else 0.0

            return jankPercent to fps
        }
    }

    override fun onDestroy() {
        isRunning.set(false)
        scope.cancel()
        try {
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(loadUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TelemetryService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentLoad = computeLoad.get()
        val effectiveLoad = if (isBatterySaverOn) max(0, currentLoad - 1) else currentLoad

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telemetry Lab running")
            .setContentText("Load: $currentLoad→$effectiveLoad | Rate: ${if (isBatterySaverOn) "10" else "20"} Hz")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telemetry Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notification for running Telemetry Lab"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "telemetry_chan"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "com.uifusion.telemetrylab.ACTION_STOP"
        const val ACTION_METRICS = "com.uifusion.telemetrylab.ACTION_METRICS"
        const val ACTION_UPDATE_LOAD = "com.uifusion.telemetrylab.ACTION_UPDATE_LOAD"
        const val ACTION_BATTERY_STATUS = "com.uifusion.telemetrylab.ACTION_BATTERY_STATUS"
        const val EXTRA_JANK = "extra_jank"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_LOAD = "com.uifusion.telemetrylab.EXTRA_LOAD"
        const val EXTRA_CURRENT_LOAD = "extra_current_load"
        const val EXTRA_EFFECTIVE_LOAD = "extra_effective_load"
        const val EXTRA_BATTERY_SAVER = "extra_battery_saver"
    }
}
