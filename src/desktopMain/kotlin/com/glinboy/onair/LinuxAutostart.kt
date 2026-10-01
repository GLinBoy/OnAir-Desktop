package com.glinboy.onair

import java.io.File
import java.io.IOException
import java.util.logging.Level

/**
 * Linux autostart via the XDG autostart spec — **VERIFIED** on the development machine.
 *
 * Writes `onair.desktop` into [AppPaths.linuxAutostartDir] (`$XDG_CONFIG_HOME/autostart`, default
 * `~/.config/autostart`); desktop environments launch everything in that directory at login.
 * Disabling deletes the file.
 */
internal class LinuxAutostart(
    private val launchCommand: List<String> = AppLaunchCommand.build(),
    private val autostartDir: File = AppPaths.linuxAutostartDir(),
) : AutostartManager {

    private val desktopFile = File(autostartDir, DESKTOP_FILE_NAME)

    override fun isEnabled(): Boolean = desktopFile.isFile

    override fun setEnabled(enabled: Boolean): Boolean = try {
        if (enabled) {
            if (!autostartDir.isDirectory && !autostartDir.mkdirs() && !autostartDir.isDirectory) {
                throw IOException("Could not create autostart directory ${autostartDir.path}")
            }
            desktopFile.writeText(desktopEntry())
            AUTOSTART_LOGGER.info("Enabled Linux autostart via ${desktopFile.path}")
        } else if (desktopFile.exists() && !desktopFile.delete()) {
            throw IOException("Could not delete ${desktopFile.path}")
        } else {
            AUTOSTART_LOGGER.info("Removed Linux autostart entry ${desktopFile.path}")
        }
        true
    } catch (t: Throwable) {
        AUTOSTART_LOGGER.log(
            Level.WARNING,
            "Could not ${if (enabled) "enable" else "disable"} Linux autostart at ${desktopFile.path}.",
            t,
        )
        false
    }

    /** Exposed for tests: the exact `.desktop` contents that [setEnabled] writes. */
    internal fun desktopEntry(): String = buildString {
        appendLine("[Desktop Entry]")
        appendLine("Type=Application")
        appendLine("Name=OnAir")
        appendLine("Comment=Shows live microphone/webcam usage in the system tray")
        appendLine("Exec=${escapeExec(launchCommand)}")
        appendLine("Terminal=false")
        appendLine("X-GNOME-Autostart-enabled=true")
        appendLine("Categories=Utility;")
    }

    private companion object {
        const val DESKTOP_FILE_NAME = "onair.desktop"

        /**
         * Quotes each argument and escapes the characters the Desktop Entry spec reserves inside
         * quoted arguments: `"`, `` ` ``, `$` and `\`.
         */
        fun escapeExec(command: List<String>): String = command.joinToString(" ") { argument ->
            "\"" + argument
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("`", "\\`")
                .replace("$", "\\$") + "\""
        }
    }
}
