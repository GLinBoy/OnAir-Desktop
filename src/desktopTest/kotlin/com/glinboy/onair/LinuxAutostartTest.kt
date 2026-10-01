package com.glinboy.onair

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxAutostartTest {

    private val command = listOf("/usr/bin/onair", "--minimized")

    private fun tempDir(): File = Files.createTempDirectory("onair-autostart").toFile()

    @Test
    fun `enable creates desktop file and disable removes it`() {
        val directory = tempDir()
        val autostart = LinuxAutostart(launchCommand = command, autostartDir = directory)

        assertFalse(autostart.isEnabled())

        assertTrue(autostart.setEnabled(true))
        assertTrue(autostart.isEnabled())
        assertTrue(File(directory, "onair.desktop").isFile)

        assertTrue(autostart.setEnabled(false))
        assertFalse(autostart.isEnabled())
        assertFalse(File(directory, "onair.desktop").exists())
    }

    @Test
    fun `desktop entry has required keys and quoted Exec`() {
        val autostart = LinuxAutostart(launchCommand = command, autostartDir = tempDir())

        val entry = autostart.desktopEntry()

        assertTrue(entry.contains("[Desktop Entry]"))
        assertTrue(entry.contains("Type=Application"))
        assertTrue(entry.contains("Name=OnAir"))
        assertTrue(entry.contains("""Exec="/usr/bin/onair" "--minimized""""))
        assertTrue(entry.contains("Terminal=false"))
    }

    @Test
    fun `disable on a missing entry is a no-op success`() {
        val autostart = LinuxAutostart(launchCommand = command, autostartDir = tempDir())

        assertTrue(autostart.setEnabled(false))
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun `Exec escaping handles spaces and reserved characters`() {
        val autostart = LinuxAutostart(
            launchCommand = listOf("/opt/My App/onair", "--flag=\$HOME"),
            autostartDir = tempDir(),
        )

        val exec = autostart.desktopEntry().lineSequence().first { it.startsWith("Exec=") }

        assertEquals("""Exec="/opt/My App/onair" "--flag=\${'$'}HOME"""", exec)
    }
}
