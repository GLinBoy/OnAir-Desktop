package com.glinboy.onair

import kotlinx.serialization.Serializable

/**
 * User-configurable settings, persisted locally as JSON (Phase 5). Shared between targets so a
 * future mobile companion can reuse the same shape; only the desktop target provides a file-backed
 * [SettingsRepository] today.
 *
 * Every field has a safe default so a missing or partially-written file still yields a usable
 * configuration.
 */
@Serializable
data class AppSettings(
    val monitorMic: Boolean = DEFAULT_MONITOR_MIC,
    val monitorCam: Boolean = DEFAULT_MONITOR_CAM,
    val pollingIntervalMs: Long = ConfigurableMediaMonitor.DEFAULT_POLLING_INTERVAL_MS,
    val startOnLogin: Boolean = DEFAULT_START_ON_LOGIN,
) {
    /** Clamps values to the ranges the rest of the app assumes. Applied on both load and save. */
    fun sanitized(): AppSettings = copy(
        pollingIntervalMs = pollingIntervalMs.coerceAtLeast(ConfigurableMediaMonitor.MIN_POLLING_INTERVAL_MS),
    )

    companion object {
        const val DEFAULT_MONITOR_MIC = true
        const val DEFAULT_MONITOR_CAM = true
        const val DEFAULT_START_ON_LOGIN = false

        /** The configuration used when no valid settings file exists. */
        val DEFAULT = AppSettings()
    }
}
