package com.glinboy.onair

/**
 * Controls the OS "start on login" mechanism for OnAir.
 *
 * Implementations are best-effort and must never throw: a missing permission or I/O failure is
 * logged and reported as a `false` return rather than crashing the app.
 */
interface AutostartManager {
    /** `true` when the OS currently has an autostart entry for OnAir. Never throws. */
    fun isEnabled(): Boolean

    /**
     * Creates ([enabled] = true) or removes ([enabled] = false) the OS autostart entry.
     *
     * @return `true` on success, `false` if the OS refused or the operation failed.
     */
    fun setEnabled(enabled: Boolean): Boolean
}

/** Creates the autostart manager for the current OS. */
expect fun createAutostartManager(): AutostartManager
