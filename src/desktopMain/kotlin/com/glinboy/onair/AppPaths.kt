package com.glinboy.onair

import java.io.File

/**
 * OS-appropriate locations for OnAir's local files. No cloud/remote storage is used anywhere.
 *
 * - Windows: `%APPDATA%\OnAir`
 * - macOS:   `~/Library/Application Support/OnAir`
 * - Linux:   `$XDG_CONFIG_HOME/onair` (default `~/.config/onair`)
 *
 * All lookups fall back to a path under the user's home directory if the usual environment
 * variables are absent, so the app always has somewhere to write.
 */
internal object AppPaths {
    const val APP_DIR_NAME = "OnAir"

    /** Directory holding persistent state (currently just `settings.json`). */
    fun configDir(): File {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            os.contains("win") -> windowsConfigDir()
            os.contains("mac") -> File(userHome(), "Library/Application Support/$APP_DIR_NAME")
            else -> File(xdgConfigHome(), APP_DIR_NAME.lowercase())
        }
    }

    /** JSON file storing [AppSettings]. */
    fun settingsFile(): File = File(configDir(), "settings.json")

    /** Linux XDG autostart directory: `$XDG_CONFIG_HOME/autostart` (default `~/.config/autostart`). */
    fun linuxAutostartDir(): File = File(xdgConfigHome(), "autostart")

    /** macOS per-user launch agents directory. */
    fun macOsLaunchAgentsDir(): File = File(userHome(), "Library/LaunchAgents")

    private fun userHome(): File = File(System.getProperty("user.home") ?: ".")

    private fun xdgConfigHome(): File {
        val xdg = System.getenv("XDG_CONFIG_HOME")
        return if (!xdg.isNullOrBlank()) File(xdg) else File(userHome(), ".config")
    }

    private fun windowsConfigDir(): File {
        val appData = System.getenv("APPDATA") ?: System.getenv("LOCALAPPDATA")
        val base = if (!appData.isNullOrBlank()) File(appData) else File(userHome(), "AppData/Roaming")
        return File(base, APP_DIR_NAME)
    }
}
