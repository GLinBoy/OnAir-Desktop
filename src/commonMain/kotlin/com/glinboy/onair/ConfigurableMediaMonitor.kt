package com.glinboy.onair

/**
 * Optional capability for monitors whose fallback polling cadence can be adjusted at runtime.
 *
 * Kept deliberately separate from [MediaMonitor] so the Phase 3 contract stays untouched: the
 * shared UI still only depends on [MediaMonitor], and a monitor opts into runtime configuration
 * only if it can honour it (Phase 4's native desktop monitor does).
 *
 * Phase 4 wires the Settings window's "Polling interval (ms)" field to this. When OS push events
 * are available the interval only bounds the safety-net poll; when they are not, it is the actual
 * detection cadence.
 */
interface ConfigurableMediaMonitor : MediaMonitor {
    /**
     * Sets the fallback polling interval in milliseconds. Values below [MIN_POLLING_INTERVAL_MS]
     * are clamped. The new value takes effect on the next poll, without restarting monitoring.
     */
    fun setPollingInterval(intervalMs: Long)

    companion object {
        const val MIN_POLLING_INTERVAL_MS = 100L
        const val DEFAULT_POLLING_INTERVAL_MS = 1_000L
    }
}
