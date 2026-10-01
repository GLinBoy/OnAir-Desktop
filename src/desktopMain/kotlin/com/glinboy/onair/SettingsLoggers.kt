package com.glinboy.onair

import java.util.logging.Logger

/** Logger for settings persistence. Failures are always logged, never silently swallowed. */
internal val SETTINGS_LOGGER: Logger = Logger.getLogger("com.glinboy.onair.settings")

/** Logger for the OS autostart integrations. */
internal val AUTOSTART_LOGGER: Logger = Logger.getLogger("com.glinboy.onair.autostart")
