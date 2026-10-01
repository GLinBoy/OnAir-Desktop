package com.glinboy.onair

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.util.logging.Level

/**
 * Windows autostart via the per-user `Run` registry key — **BEST-EFFORT, NOT VERIFIED** (this
 * session runs on Linux).
 *
 * `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` is read by Explorer at login for every
 * user, needs no elevation, and is easy to remove. The value `OnAir` holds the command line.
 */
internal class WindowsAutostart(
    private val launchCommand: List<String> = AppLaunchCommand.build(),
) : AutostartManager {

    override fun isEnabled(): Boolean = try {
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
    } catch (t: Throwable) {
        AUTOSTART_LOGGER.log(Level.FINE, "Could not read Windows Run key.", t)
        false
    }

    override fun setEnabled(enabled: Boolean): Boolean = try {
        if (enabled) {
            Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, RUN_KEY)
            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER,
                RUN_KEY,
                VALUE_NAME,
                windowsCommand(),
            )
            AUTOSTART_LOGGER.info("Enabled Windows autostart via HKCU\\$RUN_KEY\\$VALUE_NAME")
        } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
            AUTOSTART_LOGGER.info("Removed Windows autostart value HKCU\\$RUN_KEY\\$VALUE_NAME")
        }
        true
    } catch (t: Throwable) {
        AUTOSTART_LOGGER.log(
            Level.WARNING,
            "Could not ${if (enabled) "enable" else "disable"} Windows autostart.",
            t,
        )
        false
    }

    /** Exposed for tests: the command line stored in the Run value. */
    internal fun windowsCommand(): String = launchCommand.joinToString(" ") { argument ->
        if (argument.isBlank() || argument.any { it.isWhitespace() || it == '"' }) {
            "\"" + argument.replace("\"", "\\\"") + "\""
        } else {
            argument
        }
    }

    private companion object {
        const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val VALUE_NAME = "OnAir"
    }
}
