package com.glinboy.onair

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonSettingsRepositoryTest {

    private fun tempFile(): File =
        File(Files.createTempDirectory("onair-settings").toFile(), "settings.json")

    @Test
    fun `missing file yields defaults`() {
        val file = tempFile()
        val repository = JsonSettingsRepository(file)

        assertEquals(AppSettings.DEFAULT, repository.load())
    }

    @Test
    fun `round trips all fields`() {
        val file = tempFile()
        val repository = JsonSettingsRepository(file)
        val settings = AppSettings(
            monitorMic = false,
            monitorCam = false,
            pollingIntervalMs = 2_500L,
            startOnLogin = true,
        )

        repository.save(settings)

        assertTrue(file.isFile)
        assertEquals(settings, repository.load())
    }

    @Test
    fun `corrupt file yields defaults`() {
        val file = tempFile()
        file.writeText("{ this is not valid json ]")
        val repository = JsonSettingsRepository(file)

        assertEquals(AppSettings.DEFAULT, repository.load())
    }

    @Test
    fun `blank file yields defaults`() {
        val file = tempFile()
        file.writeText("   ")
        val repository = JsonSettingsRepository(file)

        assertEquals(AppSettings.DEFAULT, repository.load())
    }

    @Test
    fun `unknown keys are ignored`() {
        val file = tempFile()
        file.writeText("""{"monitorMic":false,"someFutureSetting":42}""")
        val repository = JsonSettingsRepository(file)

        val loaded = repository.load()
        assertEquals(false, loaded.monitorMic)
        assertEquals(AppSettings.DEFAULT.monitorCam, loaded.monitorCam)
    }

    @Test
    fun `polling interval is clamped on load and save`() {
        val file = tempFile()
        file.writeText("""{"pollingIntervalMs":5}""")
        val repository = JsonSettingsRepository(file)

        assertEquals(ConfigurableMediaMonitor.MIN_POLLING_INTERVAL_MS, repository.load().pollingIntervalMs)

        repository.save(AppSettings(pollingIntervalMs = 1))
        assertEquals(ConfigurableMediaMonitor.MIN_POLLING_INTERVAL_MS, repository.load().pollingIntervalMs)
    }

    @Test
    fun `save is atomic and leaves no temp files behind`() {
        val directory = Files.createTempDirectory("onair-settings").toFile()
        val repository = JsonSettingsRepository(File(directory, "settings.json"))

        repository.save(AppSettings(startOnLogin = true))

        val leftovers = directory.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "expected no leftover temp files, found $leftovers")
    }
}
