package com.glinboy.onair

/** Desktop `actual`: settings are persisted to a local JSON file in the OS config directory. */
actual fun createSettingsRepository(): SettingsRepository = JsonSettingsRepository()
