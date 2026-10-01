package com.glinboy.onair

/**
 * Loads and stores [AppSettings]. Implementations must never throw: an unreadable or corrupt store
 * is reported through the platform logger and [load] falls back to [AppSettings.DEFAULT], while a
 * failed [save] is logged and dropped so the app keeps running.
 */
interface SettingsRepository {
    /** Reads the persisted settings, or [AppSettings.DEFAULT] if none/`corrupt`. Never throws. */
    fun load(): AppSettings

    /** Persists [settings]. Best-effort: I/O errors are logged, not propagated. */
    fun save(settings: AppSettings)
}

/** Creates the platform's [SettingsRepository] (desktop: JSON file in the OS config directory). */
expect fun createSettingsRepository(): SettingsRepository
