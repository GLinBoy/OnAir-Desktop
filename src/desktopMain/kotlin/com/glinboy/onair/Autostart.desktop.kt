package com.glinboy.onair

import java.util.logging.Level

/**
 * Desktop `actual`: selects the OS-specific [AutostartManager].
 *
 * Verification status matches the Phase 4 native detectors — only one OS is available per session:
 *  - **Linux**   — implemented and verified (XDG autostart `.desktop`).
 *  - **Windows** — best-effort, `HKCU\...\Run` via JNA but NOT testable in this session.
 *  - **macOS**   — best-effort, `~/Library/LaunchAgents` plist but NOT testable in this session.
 *
 * Any unexpected initialisation failure degrades to a no-op manager so the app still runs.
 */
actual fun createAutostartManager(): AutostartManager {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return try {
        when {
            os.contains("win") -> WindowsAutostart()
            os.contains("mac") -> MacOsAutostart()
            os.contains("nux") || os.contains("nix") || os.contains("aix") -> LinuxAutostart()
            else -> NoOpAutostart(os)
        }
    } catch (t: Throwable) {
        AUTOSTART_LOGGER.log(Level.WARNING, "Could not initialise autostart for os.name='$os'.", t)
        NoOpAutostart(os)
    }
}

/** Fallback for an unrecognised OS: reports disabled and refuses changes, logging once. */
internal class NoOpAutostart(private val os: String) : AutostartManager {
    private var warned = false

    private fun warnOnce(): Boolean {
        if (!warned) {
            warned = true
            AUTOSTART_LOGGER.warning("No autostart integration for os.name='$os'; start-on-login is unavailable.")
        }
        return false
    }

    override fun isEnabled(): Boolean = false

    override fun setEnabled(enabled: Boolean): Boolean = warnOnce()
}
