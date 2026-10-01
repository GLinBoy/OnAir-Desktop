package com.glinboy.onair

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(mediaMonitor: MediaMonitor, modifier: Modifier = Modifier) {
    val isMicInUse by mediaMonitor.isMicInUse.collectAsState()
    val isCamInUse by mediaMonitor.isCamInUse.collectAsState()

    var monitorMic by remember { mutableStateOf(true) }
    var monitorCam by remember { mutableStateOf(true) }
    var pollingInterval by remember { mutableStateOf("1000") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "OnAir Settings",
            style = MaterialTheme.typography.headlineSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            StatusLed(label = "Mic", active = isMicInUse)
            StatusLed(label = "Cam", active = isCamInUse)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = monitorMic, onCheckedChange = { monitorMic = it })
                Spacer(Modifier.width(12.dp))
                Text("Monitor Mic")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = monitorCam, onCheckedChange = { monitorCam = it })
                Spacer(Modifier.width(12.dp))
                Text("Monitor Cam")
            }
        }

        OutlinedTextField(
            value = pollingInterval,
            onValueChange = { value -> pollingInterval = value.filter { it.isDigit() } },
            label = { Text("Polling interval (ms)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Mic/Cam LEDs are driven live by the shared MediaMonitor (fake data for now).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatusLed(label: String, active: Boolean) {
    val color = if (active) Color(0xFF2E7D32) else Color(0xFFBDBDBD)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
