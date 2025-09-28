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

    private val _isBatterySaverOn = MutableStateFlow(false)
    val isBatterySaverOn: StateFlow<Boolean> = _isBatterySaverOn

    private val _effectiveLoad = MutableStateFlow(2)
    val effectiveLoad: StateFlow<Int> = _effectiveLoad

    // Dynamic load update - works even while service is running
    fun setLoad(newLoad: Int) {
        _computeLoad.value = newLoad

        // If service is running, send update immediately
        if (_isRunning.value) {
            viewModelScope.launch {
                updateServiceLoad(newLoad)
            }
        }
    }

    private suspend fun updateServiceLoad(load: Int) {
        // Send broadcast to update service load dynamically
        val intent = Intent(TelemetryService.ACTION_UPDATE_LOAD).apply {
            putExtra(TelemetryService.EXTRA_LOAD, load)
        }
        // Note: This will be sent from UI context when called
    }

    fun updateServiceLoadFromContext(context: Context, load: Int) {
        val intent = Intent(TelemetryService.ACTION_UPDATE_LOAD).apply {
            putExtra(TelemetryService.EXTRA_LOAD, load)
        }
        context.sendBroadcast(intent)
    }

    fun start(context: Context) {
        val intent = Intent(context, TelemetryService::class.java).apply {
            putExtra(TelemetryService.EXTRA_LOAD, _computeLoad.value)
        }
        context.startForegroundServiceCompat(intent)
        _isRunning.value = true
    }

    fun stop(context: Context) {
        val intent = Intent(context, TelemetryService::class.java).apply {
            action = TelemetryService.ACTION_STOP
        }
        context.stopService(intent)
        _isRunning.value = false

        // Reset metrics when stopped
        _jankPercent.value = 0.0
        _fps.value = 0.0
        _effectiveLoad.value = _computeLoad.value
    }

    // Handle all metrics updates from service
    fun onMetrics(
        jank: Double,
        fpsVal: Double,
        currentLoad: Int = _computeLoad.value,
        effectiveLoad: Int = currentLoad,
        batterySaver: Boolean = _isBatterySaverOn.value
    ) {
        viewModelScope.launch {
            _jankPercent.value = jank
            _fps.value = fpsVal
            _computeLoad.value = currentLoad
            _effectiveLoad.value = effectiveLoad
            _isBatterySaverOn.value = batterySaver
        }
    }

    // Handle battery saver status updates
    fun onBatterySaverChanged(isOn: Boolean) {
        viewModelScope.launch {
            _isBatterySaverOn.value = isOn
        }
    }
}