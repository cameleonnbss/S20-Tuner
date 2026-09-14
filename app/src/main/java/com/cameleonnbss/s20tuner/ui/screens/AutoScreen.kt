package com.cameleonnbss.s20tuner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cameleonnbss.s20tuner.ui.*
import com.cameleonnbss.s20tuner.ui.TunerViewModel

@Composable
fun AutoScreen(vm: TunerViewModel) {
    val running by vm.autoRunning.collectAsStateWithLifecycle()
    val mode by vm.autoMode.collectAsStateWithLifecycle()
    val state by vm.autoState.collectAsStateWithLifecycle()
    val autoLog by vm.autoLog.collectAsStateWithLifecycle()
    val st by vm.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshAuto() }

    ScreenScaffold("Auto OC / Underclock", "The daemon watches screen state + CPU load and re-tunes clocks every 2s") {

        SectionCard(
            "Engine",
            subtitle = if (running) "RUNNING — reacting to load" else "STOPPED — stock clock behavior"
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text(if (running) "Auto OC/UC is ON" else "Auto OC/UC is OFF", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !running -> "flip the switch to start"
                            state == "perf" -> "state: PERF — overclocked"
                            state == "eco" -> "state: ECO — underclocked (screen off)"
                            else -> "state: BAL — normal"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state == "perf") Green else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(checked = running, onCheckedChange = { vm.setAuto(it, mode) })
            }
            Text("Current load: ${(st.load * 100).toInt()}%  ·  CPU big: ${(st.cpuFreqs.getOrNull(1) ?: 0) / 1000} MHz", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }

        SectionCard("Mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("auto", "gaming", "battery").forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { vm.changeAutoMode(m) },
                        label = {
                            Text(
                                when (m) {
                                    "auto" -> "Adaptive"
                                    "gaming" -> "Gaming (aggressive OC)"
                                    else -> "Battery (eco bias)"
                                }
                            )
                        }
                    )
                }
            }
            Text(
                when (mode) {
                    "gaming" -> "PERF triggers at 120% load, higher clocks held longer."
                    "battery" -> "PERF needs 400% load; ECO caps are 10% lower."
                    else -> "PERF at 250% load; ECO whenever the screen turns off."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        SectionCard("How it works") {
            Text("• Screen off → ECO: big cores capped ~65%, GPU capped 55% — battery saved while idle.", style = MaterialTheme.typography.bodySmall)
            Text("• Load spike → PERF: min clocks raised (auto-OC), GPU pinned near max for instant response.", style = MaterialTheme.typography.bodySmall)
            Text("• Light use → BAL: full range, schedutil decides — no wasted heat.", style = MaterialTheme.typography.bodySmall)
            Text("Runs as root daemon in /data/adb/s20tuner_auto.sh — survives app close, stops cleanly and restores stock clocks when you switch it off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        SectionCard("Daemon log") {
            ActionButton("Refresh log") { vm.readAutoLog() }
            if (autoLog.isNotBlank()) {
                Text(autoLog, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.height(160.dp))
            }
        }
    }
}
