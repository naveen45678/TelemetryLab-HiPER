package com.example.telemetrylab.data

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uifusion.telemetrylab.service.TelemetryService
import com.uifusion.telemetrylab.util.startForegroundServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TelemetryViewModel : ViewModel() {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _computeLoad = MutableStateFlow(2)
    val computeLoad: StateFlow<Int> = _computeLoad

    private val _jankPercent = MutableStateFlow(0.0)
    val jankPercent: StateFlow<Double> = _jankPercent

    private val _fps = MutableStateFlow(0.0)
    val fps: StateFlow<Double> = _fps

    fun setLoad(v: Int) { _computeLoad.value = v }

    fun start(context: Context) {
        val intent = Intent(context, TelemetryService::class.java).apply {
            putExtra(TelemetryService.EXTRA_LOAD, _computeLoad.value)
        }
        context.startForegroundServiceCompat(intent)
        _isRunning.value = true
    }

    fun stop(context: Context) {
        val intent = Intent(context, TelemetryService::class.java)
        intent.action = TelemetryService.ACTION_STOP
        context.stopService(intent)
        _isRunning.value = false
    }

    fun onMetrics(jank: Double, fpsVal: Double) {
        viewModelScope.launch {
            _jankPercent.value = jank
            _fps.value = fpsVal
        }
    }
}


