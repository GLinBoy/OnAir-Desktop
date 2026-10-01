package com.glinboy.onair

/**
 * Desktop `actual`: Phase 4 replaces the Phase 3 fake with real per-OS detection. The returned
 * object also implements [ConfigurableMediaMonitor] so the entry point can forward the Settings
 * window's polling-interval field to it.
 */
actual fun createMediaMonitor(): MediaMonitor = NativeMediaMonitor()
