package com.cameleonnbss.s20tuner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cameleonnbss.s20tuner.ui.*
import com.cameleonnbss.s20tuner.ui.TunerViewModel

@Composable
fun BatteryScreen(vm: TunerViewModel) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val st by vm.status.collectAsStateWithLifecycle()

    val watts = if (st.battCurrentMa != 0 && st.battVoltageUv > 0)
        (st.battCurrentMa * st.battVoltageUv / 1_000_000f) else 0f

    ScreenScaffold("Battery", "Charge limits and fast-charge control") {

        SectionCard("Live") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatTile("Level", "${st.battPct}%", Modifier.weight(1f))
                StatTile("Status", st.battCharging.ifBlank { "?" }, Modifier.weight(1f))
                StatTile("Power", "%.1f W".format(kotlin.math.abs(watts)), Modifier.weight(1f))
            }
            StatRow("Current", "${st.battCurrentMa} mA")
            StatRow("Voltage", "%.3f V".format(st.battVoltageUv / 1_000_000f))
            StatRow("Temp", "%.1f °C".format(st.tempBat))
        }

        SectionCard("Charge limit", subtitle = "Stops full-charge stress — 80%% recommended for longevity") {
            LabeledSlider(
                "Max charge", (cfg.chargeLimit.takeIf { it > 0 }?.toFloat() ?: 100f),
                { vm.setConfig(cfg.copy(chargeLimit = it.toInt())) },
                50f..100f, steps = 9,
                format = { if (it >= 100f) "unlimited" else "${it.toInt()}%" }
            )
        }

        SectionCard("Fast charge") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("off", "25", "45").forEach { m ->
                    FilterChip(
                        selected = cfg.fastCharge == m,
                        onClick = { vm.setConfig(cfg.copy(fastCharge = m)) },
                        label = { Text(if (m == "off") "Off" else if (m == "25") "25W" else "45W super") }
                    )
                }
            }
        }

        SectionCard("Idle drain / wakelocks", subtitle = "Suspends the noisiest background services") {
            val wls = listOf(
                "com.samsung.android.app.spage" to "Samsung Free",
                "com.samsung.android.rubin.app" to "Customization Service",
                "com.samsung.android.beaconmanager" to "Beacon Manager",
                "com.sec.android.diagmonagent" to "Diagnostics",
                "com.facebook.system" to "Facebook Installer"
            )
            wls.forEach { (pkg, name) ->
                ToggleRow(name, pkg in cfg.blockWakelocks, { on ->
                    val l = cfg.blockWakelocks.toMutableList()
                    if (on) l.add(pkg) else l.remove(pkg)
                    vm.setConfig(cfg.copy(blockWakelocks = l))
                }, subtitle = pkg)
            }
            Text("Applying suspends these packages (pm suspend) — reversible from the Debloat tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        ActionButton("Apply battery settings", modifier = Modifier.fillMaxWidth(), primary = true) { vm.applyConfig() }
    }
}
