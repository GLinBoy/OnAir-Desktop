package com.glinboy.onair

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator
import dorkbox.systemTray.SystemTray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.awt.EventQueue

fun main() {
    // Load persisted settings before the composition starts, then reuse the same store/manager
    // throughout the app lifetime. Loading is a tiny local read; a failure already falls back to
    // defaults inside the repository.
    val settingsRepository = createSettingsRepository()
    val autostartManager = createAutostartManager()
    val initialSettings = settingsRepository.load()

    application {
        var settings by remember { mutableStateOf(initialSettings) }
        var settingsVisible by remember { mutableStateOf(false) }
        val mediaMonitor = remember { createMediaMonitor() }
        val isMicInUse by mediaMonitor.isMicInUse.collectAsState()
        val isCamInUse by mediaMonitor.isCamInUse.collectAsState()
        val anyMediaInUse = isMicInUse || isCamInUse
        var tray by remember { mutableStateOf<SystemTray?>(null) }

        val settingsWindowState = rememberWindowState(
            size = DpSize(460.dp, 480.dp),
            position = WindowPosition(Alignment.Center),
        )

        /**
         * Persists the new settings, applies the polling interval, and (only when the start-on-login
         * preference actually changed) creates/removes the OS autostart entry. If the OS refuses the
         * autostart change we revert just that field so the UI and the saved file stay truthful.
         */
        fun applySettings(updated: AppSettings) {
            var effective = updated
            if (updated.startOnLogin != settings.startOnLogin) {
                val applied = autostartManager.setEnabled(updated.startOnLogin)
                if (!applied) {
                    AUTOSTART_LOGGER.warning(
                        "The OS refused to ${if (updated.startOnLogin) "enable" else "disable"} " +
                            "start-on-login; reverting the toggle.",
                    )
                    effective = updated.copy(startOnLogin = settings.startOnLogin)
                }
            }
            settings = effective
            settingsRepository.save(effective)
            (mediaMonitor as? ConfigurableMediaMonitor)?.setPollingInterval(effective.pollingIntervalMs)
        }

        LaunchedEffect(Unit) {
            val systemTray = withContext(Dispatchers.IO) { SystemTray.get("OnAir") }
            if (systemTray == null) {
                settingsVisible = true
            } else {
                systemTray.setTooltip("OnAir")
                systemTray.menu.apply {
                    add(MenuItem("Open Settings") { EventQueue.invokeLater { settingsVisible = true } })
                    add(Separator())
                    add(MenuItem("Quit") { EventQueue.invokeLater { exitApplication() } })
                }
                tray = systemTray
            }
        }

        // Keep the OS autostart entry in sync with the saved preference: if it was removed manually
        // (or never created), recreate it; if the preference is off, make sure no entry lingers.
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                if (autostartManager.isEnabled() != settings.startOnLogin) {
                    autostartManager.setEnabled(settings.startOnLogin)
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose { tray?.shutdown() }
        }

        // Start the monitor for the lifetime of the app and cancel its background work cleanly
        // when the composition is disposed on exit, so no coroutine/thread is leaked.
        DisposableEffect(mediaMonitor) {
            (mediaMonitor as? ConfigurableMediaMonitor)?.setPollingInterval(settings.pollingIntervalMs)
            mediaMonitor.startMonitoring()
            onDispose { mediaMonitor.stopMonitoring() }
        }

        // The tray lives outside Compose, so keep the application's composition active until
        // exitApplication() cancels it; otherwise `application` ends on launch (no window/tray).
        LaunchedEffect(Unit) {
            awaitCancellation()
        }

        LaunchedEffect(anyMediaInUse, tray) {
            tray?.setImage(trayImage(anyMediaInUse))
        }

        if (settingsVisible) {
            Window(
                onCloseRequest = { if (tray == null) exitApplication() else settingsVisible = false },
                state = settingsWindowState,
                title = "OnAir — Settings",
            ) {
                SettingsScreen(
                    mediaMonitor = mediaMonitor,
                    settings = settings,
                    onSettingsChange = { applySettings(it) },
                )
            }
        }
    }
}
