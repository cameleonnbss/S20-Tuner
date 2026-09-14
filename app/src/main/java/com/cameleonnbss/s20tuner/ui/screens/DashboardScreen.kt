package com.cameleonnbss.s20tuner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cameleonnbss.s20tuner.ui.*

@Composable
fun DashboardScreen(vm: TunerViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val st by vm.status.collectAsStateWithLifecycle()
    val cfg by vm.config.collectAsStateWithLifecycle()

    ScreenScaffold("Dashboard") {
        // --- warnings ---
        if (!ui.rootOk) {
            SectionCard("⚠ No root", subtitle = "Grant superuser to the app in Magisk, then reopen it.") {
                Text(
                    "The tuner needs root (su) to read/write kernel nodes.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if (!ui.device.isSupported && ui.device.device.isNotBlank()) {
            SectionCard("⚠ Device not officially supported", subtitle = "Tweaks are guarded and only touch nodes that exist on your kernel.") {
                Text("Detected: ${ui.device.model} — ${ui.device.device}", style = MaterialTheme.typography.bodySmall)
            }
        }

        // --- device card ---
        SectionCard("Device") {
            StatRow("Model", ui.device.model.ifBlank { "…" })
            StatRow("Codename", ui.device.codename.ifBlank { "…" })
            StatRow("SoC", ui.device.soc.ifBlank { "…" })
            StatRow("Android", ui.device.android)
            StatRow("Kernel", ui.device.kernel)
            StatRow("ROM", ui.device.buildId)
            StatRow("Root", if (ui.rootOk) "OK" else "not granted")
        }

        // --- live telemetry ---
        SectionCard("Live telemetry") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatTile("CPU big", "${(st.cpuFreqs.getOrNull(1) ?: 0) / 1000} MHz", Modifier.weight(1f))
                StatTile("Prime", "${(st.cpuFreqs.getOrNull(2) ?: 0) / 1000} MHz", Modifier.weight(1f))
                StatTile("GPU", "${st.gpuFreq / 1000} MHz", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatTile("Batt", "${st.battPct}%", Modifier.weight(1f), accent = if (st.battPct < 20) Red else Green)
                StatTile("Batt temp", "%.1f°C".format(st.tempBat), Modifier.weight(1f), accent = if (st.tempBat > 38f) Red else Green)
                StatTile("CPU temp", "%.1f°C".format(st.tempCpu), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatTile("GPU busy", "${st.gpuBusy}%", Modifier.weight(1f))
                StatTile("Free RAM", "${st.memAvailKb / 1024} MB", Modifier.weight(1f))
                StatTile("sconfig", st.thermalConfig.ifBlank { "?" }, Modifier.weight(1f))
            }
            Text("CPU (big cluster) MHz", style = MaterialTheme.typography.labelSmall)
            MiniGraph(vm.cpuHist.value.map { it.toFloat() }, maxValue = 2_800_000f)
            Text("GPU busy %", style = MaterialTheme.typography.labelSmall)
            MiniGraph(vm.gpuHist.value.map { it.toFloat() })
            Text("Battery temp", style = MaterialTheme.typography.labelSmall)
            MiniGraph(vm.tempHist.value, color = Amber, maxValue = 60f)
        }

        // --- quick profiles ---
        SectionCard("Quick profiles") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TunerViewModel.presets.forEach { p ->
                    ActionButton(p.name, Modifier.weight(1f), primary = p.name == "Gaming") { vm.applyPreset(p) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Boot persist:", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = ui.bootPersistEnabled, onCheckedChange = { vm.setBootPersist(it) })
                Text(if (ui.bootPersistEnabled) "ON — re-applied at boot" else "OFF", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Apply current config", Modifier.weight(1f), primary = true) { vm.applyConfig() }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

