package com.glinboy.onair

import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level

/**
 * File-backed [SettingsRepository] that stores [AppSettings] as pretty-printed JSON at
 * [settingsFile] (defaults to [AppPaths.settingsFile]).
 *
 * Robustness: a missing, empty, or corrupt file yields [AppSettings.DEFAULT] and a logged warning;
 * writes go to a temp file first and are moved into place so a crash mid-write cannot leave a
 * half-written settings file. All I/O failures are caught and logged, never propagated.
 */
internal class JsonSettingsRepository(
    private val settingsFile: File = AppPaths.settingsFile(),
) : SettingsRepository {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun load(): AppSettings {
        if (!settingsFile.isFile) return AppSettings.DEFAULT
        return try {
            val text = settingsFile.readText()
            if (text.isBlank()) {
                AppSettings.DEFAULT
            } else {
                json.decodeFromString(AppSettings.serializer(), text).sanitized()
            }
        } catch (t: Throwable) {
            SETTINGS_LOGGER.log(
                Level.WARNING,
                "Could not read settings from ${settingsFile.path}; falling back to defaults.",
                t,
            )
            AppSettings.DEFAULT
        }
    }

    override fun save(settings: AppSettings) {
        val sanitized = settings.sanitized()
        try {
            writeAtomically(json.encodeToString(AppSettings.serializer(), sanitized))
        } catch (t: Throwable) {
            SETTINGS_LOGGER.log(
                Level.WARNING,
                "Could not persist settings to ${settingsFile.path}; changes are not saved.",
                t,
            )
        }
    }

    /** Writes to a sibling temp file, then atomically replaces the target where the FS supports it. */
    private fun writeAtomically(content: String) {
        val directory = settingsFile.parentFile ?: File(".")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Could not create settings directory ${directory.path}")
        }

        val temp = File.createTempFile("settings", ".json.tmp", directory)
        try {
            temp.writeText(content)
            val source = temp.toPath()
            val target = settingsFile.toPath()
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (t: Throwable) {
                // Some filesystems (or a cross-device temp) reject ATOMIC_MOVE; a plain replace is fine.
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
