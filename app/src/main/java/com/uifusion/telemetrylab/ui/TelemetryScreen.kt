package com.uifusion.telemetrylab.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    viewModel: TelemetryViewModel= viewModel()
) {
    val context = LocalContext.current

    val jank by viewModel.jankPercent.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val computeLoad by viewModel.computeLoad.collectAsState()

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == TelemetryService.ACTION_METRICS) {
                    val j = intent.getDoubleExtra(TelemetryService.EXTRA_JANK, 0.0)
                    val f = intent.getDoubleExtra(TelemetryService.EXTRA_FPS, 0.0)
                    viewModel.onMetrics(j, f)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(TelemetryService.ACTION_METRICS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Stats Panel
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Telemetry Stats", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Jank %: ${String.format("%.2f", jank)}")
                Text("FPS: ${String.format("%.2f", fps)}")
            }
        }

        Column {
            Text("Compute Load: $computeLoad")
            Slider(
                value = computeLoad.toFloat(),
                onValueChange = { viewModel.setLoad(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { viewModel.start(context) },
                enabled = !isRunning
            ) { Text("Start") }

            Button(
                onClick = { viewModel.stop(context) },
                enabled = isRunning
            ) { Text("Stop") }
        }
    }
}

