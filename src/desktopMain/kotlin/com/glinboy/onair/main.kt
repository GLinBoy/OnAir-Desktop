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

fun main() = application {
    var settingsVisible by remember { mutableStateOf(false) }
    val mediaMonitor = remember { createMediaMonitor() }
    val isMicInUse by mediaMonitor.isMicInUse.collectAsState()
    val isCamInUse by mediaMonitor.isCamInUse.collectAsState()
    val anyMediaInUse = isMicInUse || isCamInUse
    var tray by remember { mutableStateOf<SystemTray?>(null) }

    val settingsWindowState = rememberWindowState(
        size = DpSize(460.dp, 420.dp),
        position = WindowPosition(Alignment.Center),
    )

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

    DisposableEffect(Unit) {
        onDispose { tray?.shutdown() }
    }

    // Start the monitor for the lifetime of the app and cancel its background work cleanly
    // when the composition is disposed on exit, so no coroutine/thread is leaked.
    DisposableEffect(mediaMonitor) {
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
                onPollingIntervalChange = { intervalMs ->
                    (mediaMonitor as? ConfigurableMediaMonitor)?.setPollingInterval(intervalMs)
                },
            )
        }
    }
}
