package com.glinboy.onair

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TOGGLE_INTERVAL_MS = 4_000L

/**
 * Phase 3 placeholder [MediaMonitor]: flips its flows on a timer so the async/StateFlow wiring
 * can be validated end-to-end. It performs no hardware detection — Phase 4 replaces this
 * `actual` with a real per-OS implementation behind the same interface.
 */
class FakeMediaMonitor : MediaMonitor {
    private val _isMicInUse = MutableStateFlow(false)
    override val isMicInUse: StateFlow<Boolean> = _isMicInUse.asStateFlow()

    private val _isCamInUse = MutableStateFlow(false)
    override val isCamInUse: StateFlow<Boolean> = _isCamInUse.asStateFlow()

    private var monitoringScope: CoroutineScope? = null

    override fun startMonitoring() {
        if (monitoringScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        monitoringScope = scope
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(TOGGLE_INTERVAL_MS)
                tick++
                _isMicInUse.value = tick % 2 == 0
                _isCamInUse.value = tick % 3 == 0
            }
        }
    }

    override fun stopMonitoring() {
        monitoringScope?.cancel()
        monitoringScope = null
        _isMicInUse.value = false
        _isCamInUse.value = false
    }
}
