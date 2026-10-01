package com.glinboy.onair

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic source of live microphone/webcam usage state.
 *
 * Implementations are provided per platform via [createMediaMonitor]; the shared UI only ever
 * depends on this interface and the two [StateFlow]s, so swapping in a real OS-level
 * implementation (Phase 4) requires no changes here or in the UI layer.
 */
interface MediaMonitor {
    /** `true` while the microphone is currently being captured by some application. */
    val isMicInUse: StateFlow<Boolean>

    /** `true` while the webcam is currently being captured by some application. */
    val isCamInUse: StateFlow<Boolean>

    /** Begins observing mic/cam state. Safe to call repeatedly; a no-op while already running. */
    fun startMonitoring()

    /** Stops observing and releases any background work. Safe to call when not running. */
    fun stopMonitoring()
}
