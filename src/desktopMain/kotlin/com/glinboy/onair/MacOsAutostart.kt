package com.glinboy.onair

import java.io.File
import java.io.IOException
import java.util.logging.Level

/**
 * macOS autostart via a per-user LaunchAgent — **BEST-EFFORT, NOT VERIFIED** (this session runs on
 * Linux).
 *
 * Writes `com.glinboy.onair.plist` into `~/Library/LaunchAgents`; launchd loads agents from there
 * at login for the owning user. Disabling deletes the plist. We intentionally do not shell out to
 * `launchctl` — the file is picked up on the next login, which is exactly what the setting means.
 */
internal class MacOsAutostart(
    private val launchCommand: List<String> = AppLaunchCommand.build(),
    private val launchAgentsDir: File = AppPaths.macOsLaunchAgentsDir(),
) : AutostartManager {

    private val plistFile = File(launchAgentsDir, "$LABEL.plist")

    override fun isEnabled(): Boolean = plistFile.isFile

    override fun setEnabled(enabled: Boolean): Boolean = try {
        if (enabled) {
            if (!launchAgentsDir.isDirectory && !launchAgentsDir.mkdirs() && !launchAgentsDir.isDirectory) {
                throw IOException("Could not create LaunchAgents directory ${launchAgentsDir.path}")
            }
            plistFile.writeText(plist())
            AUTOSTART_LOGGER.info("Enabled macOS autostart via ${plistFile.path}")
        } else if (plistFile.exists() && !plistFile.delete()) {
            throw IOException("Could not delete ${plistFile.path}")
        } else {
            AUTOSTART_LOGGER.info("Removed macOS LaunchAgent ${plistFile.path}")
        }
        true
    } catch (t: Throwable) {
        AUTOSTART_LOGGER.log(
            Level.WARNING,
            "Could not ${if (enabled) "enable" else "disable"} macOS autostart at ${plistFile.path}.",
            t,
        )
        false
    }

    /** Exposed for tests: the exact plist contents that [setEnabled] writes. */
    internal fun plist(): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">""")
        appendLine("""<plist version="1.0">""")
        appendLine("<dict>")
        appendLine("  <key>Label</key>")
        appendLine("  <string>$LABEL</string>")
        appendLine("  <key>ProgramArguments</key>")
        appendLine("  <array>")
        launchCommand.forEach { argument -> appendLine("    <string>${escapeXml(argument)}</string>") }
        appendLine("  </array>")
        appendLine("  <key>RunAtLoad</key>")
        appendLine("  <true/>")
        appendLine("</dict>")
        appendLine("</plist>")
    }

    private companion object {
        const val LABEL = "com.glinboy.onair"

        fun escapeXml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
