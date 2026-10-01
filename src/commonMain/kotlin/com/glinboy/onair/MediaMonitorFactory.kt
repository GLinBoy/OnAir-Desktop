package com.glinboy.onair

/**
 * Creates the platform's [MediaMonitor]. Each target supplies an `actual` implementation
 * (Phase 3: a fake timer-driven monitor; Phase 4: real OS APIs), without the shared code
 * needing to change.
 */
expect fun createMediaMonitor(): MediaMonitor
