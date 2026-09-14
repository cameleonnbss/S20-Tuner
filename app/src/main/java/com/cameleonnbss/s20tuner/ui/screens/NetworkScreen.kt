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
fun NetworkScreen(vm: TunerViewModel) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val dev = vm.ui.collectAsStateWithLifecycle().value.device

    val algos = dev.tcpAvailable.ifEmpty { listOf("bbr", "cubic", "westwood", "reno") }

    ScreenScaffold("Network", "TCP congestion control and WiFi behavior") {

        SectionCard("TCP congestion algorithm") {
            Text("Available on this kernel: ${if (dev.tcpAvailable.isNotEmpty()) dev.tcpAvailable.joinToString(" / ") else "default list"}", style = MaterialTheme.typography.bodySmall)
            ChoiceRow(algos, cfg.tcpAlgo.takeIf { it.isNotBlank() }) { a ->
                vm.setConfig(cfg.copy(tcpAlgo = a))
            }
            Text("BBR: best latency under load · cubic: default · westwood: good on lossy mobile links.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        SectionCard("WiFi TX / power") {
            ToggleRow("WiFi TX boost", cfg.wifiTxBoost, { vm.setConfig(cfg.copy(wifiTxBoost = it)) },
                subtitle = "Disables WiFi power-save and TX glomming — more range, more battery drain")
        }

        ActionButton("Apply network settings", modifier = Modifier.fillMaxWidth(), primary = true) { vm.applyConfig() }
    }
}
