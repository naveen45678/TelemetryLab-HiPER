package com.uifusion.telemetrylab.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.telemetrylab.data.TelemetryViewModel
import com.uifusion.telemetrylab.service.TelemetryService

@Composable
fun TelemetryScreen(
    modifier: Modifier = Modifier,
    viewModel: TelemetryViewModel = viewModel()
) {
    val context = LocalContext.current

    val jank by viewModel.jankPercent.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val computeLoad by viewModel.computeLoad.collectAsState()
    val isBatterySaverOn by viewModel.isBatterySaverOn.collectAsState()
    val effectiveLoad by viewModel.effectiveLoad.collectAsState()

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    TelemetryService.ACTION_METRICS -> {
                        val j = intent.getDoubleExtra(TelemetryService.EXTRA_JANK, 0.0)
                        val f = intent.getDoubleExtra(TelemetryService.EXTRA_FPS, 0.0)
                        val currentLoad = intent.getIntExtra(TelemetryService.EXTRA_CURRENT_LOAD, computeLoad)
                        val effLoad = intent.getIntExtra(TelemetryService.EXTRA_EFFECTIVE_LOAD, computeLoad)
                        val batterySaver = intent.getBooleanExtra(TelemetryService.EXTRA_BATTERY_SAVER, false)

                        viewModel.onMetrics(j, f, currentLoad, effLoad, batterySaver)
                    }
                    TelemetryService.ACTION_BATTERY_STATUS -> {
                        val isOn = intent.getBooleanExtra(TelemetryService.EXTRA_BATTERY_SAVER, false)
                        android.util.Log.d("TelemetryScreen", "Battery saver status: $isOn")
                        viewModel.onBatterySaverChanged(isOn)
                        showToast(context, isOn)
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        // Fallback for direct system broadcast
                        val powerManager = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        val isOn = powerManager?.isPowerSaveMode ?: false
                        viewModel.onBatterySaverChanged(isOn)
                        showToast(context, isOn)
                    }
                }
            }

            private fun showToast(context: Context?, isOn: Boolean) {
                val message = if (isOn) {
                    "Battery Saver ON - Performance throttled to 10Hz"
                } else {
                    "Battery Saver OFF - Full 20Hz performance"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        val filter = IntentFilter().apply {
            addAction(TelemetryService.ACTION_METRICS)
            addAction(TelemetryService.ACTION_BATTERY_STATUS)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }

        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Receiver already unregistered
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Telemetry Stats", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Jank %: ${String.format("%.2f", jank)}")
                val fpsDisplay = if (isBatterySaverOn) "-" else String.format("%.2f", fps)
                Text("FPS: $fpsDisplay")
                if (isBatterySaverOn) {
                    Text(
                        text = "(Battery Saver ON — rate throttled)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Column {
            Text("Compute Load: $computeLoad")
            Slider(
                value = computeLoad.toFloat(),
                onValueChange = { newValue ->
                    val newLoad = newValue.toInt()
                    viewModel.setLoad(newLoad)
                    if (isRunning) {
                        viewModel.updateServiceLoadFromContext(context, newLoad)
                    }
                },
                valueRange = 0f..8f,
                steps = 7
                // Removed the enabled = !isRunning constraint - now always enabled!
            )
            if (isBatterySaverOn && computeLoad > 0) {
                Text(
                    text = "Effective Load: $effectiveLoad (reduced by battery saver)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { viewModel.start(context) },
                enabled = !isRunning
            ) {
                Text("Start")
            }
            Button(
                onClick = { viewModel.stop(context) },
                enabled = isRunning
            ) {
                Text("Stop")
            }
        }

        if (isRunning) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Status: Running",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Target Rate: ${if (isBatterySaverOn) "10" else "20"} Hz",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Active Load: $effectiveLoad (from $computeLoad)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (isBatterySaverOn) {
                        Text(
                            text = "⚡ Battery Saver Mode Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        if (isRunning) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Debug Info",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Battery Saver: $isBatterySaverOn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Slider: $computeLoad → Service: $effectiveLoad",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}