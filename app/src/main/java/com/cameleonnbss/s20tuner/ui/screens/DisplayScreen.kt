package com.cameleonnbss.s20tuner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cameleonnbss.s20tuner.core.Sysfs
import com.cameleonnbss.s20tuner.ui.*
import com.cameleonnbss.s20tuner.ui.TunerViewModel

@Composable
fun DisplayScreen(vm: TunerViewModel) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val st by vm.status.collectAsStateWithLifecycle()

    ScreenScaffold("Display", "Refresh rate via your GalaxyHz module when installed (panel-verified modes)") {

        SectionCard("Live") {
            StatRow("Reported refresh", st.refreshRate.let { if (it > 0) "$it Hz" else "?" })
            StatRow("FPS dump", st.fpsText.ifBlank { "?" })
        }

        SectionCard("Refresh rate") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(60, 96, 120).forEach { hz ->
                    FilterChip(
                        selected = cfg.refreshRate == hz,
                        onClick = { vm.setConfig(cfg.copy(refreshRate = hz)) },
                        label = { Text("$hz Hz") }
                    )
                }
            }
            ToggleRow("Lock refresh rate (anti-flicker)", cfg.lockRefreshRate, { vm.setConfig(cfg.copy(lockRefreshRate = it)) },
                subtitle = "Kills SurfaceFlinger idle/touch timers — same props as GalaxyHz")
            var touchPoll by remember(cfg.touchPollRate) { mutableStateOf((cfg.touchPollRate.takeIf { it > 0 } ?: 120).toFloat()) }
            LabeledSlider("Touch polling rate", touchPoll, { vm.setConfig(cfg.copy(touchPollRate = it.toInt())) }, 60f..240f, steps = 5, format = { "${it.toInt()} Hz" })
        }

        SectionCard("Panel") {
            ToggleRow("DC dimming", cfg.dcDimming ?: false, { vm.setConfig(cfg.copy(dcDimming = it)) },
                subtitle = "Reduces OLED PWM flicker at low brightness")
            ToggleRow("Glove / touch sensitivity", cfg.touchSensitivity ?: false, { vm.setConfig(cfg.copy(touchSensitivity = it)) })
            Text("Color gamut", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("native", "srgb", "dci").forEach { g ->
                    FilterChip(selected = cfg.colorGamut == g, onClick = { vm.setConfig(cfg.copy(colorGamut = g)) }, label = { Text(g) })
                }
            }
        }

        SectionCard("GalaxyHz module bridge", subtitle = "Detected: check runs on Apply") {
            Text(
                "If force_120hz_x1s is installed, the app writes ${Sysfs.FORCE_HZ_CONF} and triggers its action script so the panel switches to a verified mode.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        ActionButton("Apply display", modifier = Modifier.fillMaxWidth(), primary = true) { vm.applyConfig() }
    }
}
