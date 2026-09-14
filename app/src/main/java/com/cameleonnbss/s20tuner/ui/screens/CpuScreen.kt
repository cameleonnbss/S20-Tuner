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
fun CpuScreen(vm: TunerViewModel) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val dev = vm.ui.collectAsStateWithLifecycle().value.device
    val st by vm.status.collectAsStateWithLifecycle()

    val policies = dev.cpuPolicies.ifEmpty {
        com.cameleonnbss.s20tuner.core.Sysfs.cpuPolicyPaths()
    }
    val clusterNames = listOf("Little", "Mid", "Prime")
    val govs = listOf("schedutil", "performance", "powersave", "ondemand", "conservative")

    ScreenScaffold("CPU", "Per-cluster governor / freq / undervolt (Masonic vdd table)") {

        // current freqs
        SectionCard("Live") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                clusterNames.forEachIndexed { i, n ->
                    StatTile(n, "${(st.cpuFreqs.getOrNull(i) ?: 0) / 1000} MHz", Modifier.weight(1f))
                }
            }
        }

        // per-cluster cards
        for (i in 0 until 3) {
            val pol = policies.getOrNull(i) ?: continue
            val freqs = dev.policyFreqs[pol] ?: emptyList()
            SectionCard(clusterNames[i] + "  (${pol.substringAfterLast('/')})") {
                // governor choice
                Text("Governor", style = MaterialTheme.typography.labelSmall)
                ChoiceRow(govs, cfg.cpuGovernors.getOrNull(i)?.takeIf { it.isNotBlank() }) { g ->
                    val l = cfg.cpuGovernors.toMutableList(); l[i] = g; vm.setConfig(cfg.copy(cpuGovernors = l))
                }
                // min freq
                if (freqs.isNotEmpty()) {
                    val minV = (cfg.cpuMinFreqs.getOrNull(i)?.takeIf { it > 0 } ?: freqs.first()).toFloat()
                    LabeledSlider(
                        "Min frequency", minV, { v ->
                            val l = cfg.cpuMinFreqs.toMutableList(); l[i] = v.toInt(); vm.setConfig(cfg.copy(cpuMinFreqs = l))
                        },
                        freqs.first().toFloat()..freqs.last().toFloat(),
                        format = { "${(it / 1000).toInt()} MHz" }
                    )
                    val maxV = (cfg.cpuMaxFreqs.getOrNull(i)?.takeIf { it > 0 } ?: freqs.last()).toFloat()
                    LabeledSlider(
                        "Max frequency", maxV, { v ->
                            val l = cfg.cpuMaxFreqs.toMutableList(); l[i] = v.toInt(); vm.setConfig(cmdFix(cfg, i, v.toInt(), "max"))
                        },
                        freqs.first().toFloat()..freqs.last().toFloat(),
                        format = { "${(it / 1000).toInt()} MHz" }
                    )
                }
                // undervolt delta for this cluster
                var uvText by remember(i) { mutableStateOf("") }
                OutlinedTextField(
                    value = uvText,
                    onValueChange = { uvText = it },
                    label = { Text("Undervolt delta µV (e.g. -25000)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ActionButton("Set undervolt ${clusterNames[i]}") {
                    val d = uvText.toIntOrNull() ?: return@ActionButton
                    val m = cfg.cpuUvDeltas.toMutableMap()
                    // apply delta to all freqs of this policy's table
                    (dev.policyFreqs[pol] ?: emptyList()).forEach { f ->
                        m[f.toString()] = d
                    }
                    vm.setConfig(cfg.copy(cpuUvDeltas = m))
                    vm.applyConfig()
                }
            }
        }

        // thermal
        SectionCard("Thermal") {
            ToggleRow("Thermal throttling override", cfg.thermalOverride, { v -> vm.setConfig(cfg.copy(thermalOverride = v)) },
                subtitle = "Writes sconfig; may increase temps — watch your battery")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceRow(listOf("0", "3", "9"), cfg.thermalMode.takeIf { it.isNotBlank() }) { m ->
                    vm.setConfig(cfg.copy(thermalMode = m))
                }
            }
            Text("0 = normal · 3 = heavy cooling · 9 = hot override (kernel-dependent)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            ActionButton("Apply CPU + thermal", modifier = Modifier.fillMaxWidth(), primary = true) { vm.applyConfig() }
        }
    }
}

// helper kept out of the main flow
private fun cmdFix(cfg: com.cameleonnbss.s20tuner.model.TunerConfig, i: Int, v: Int, kind: String): com.cameleonnbss.s20tuner.model.TunerConfig = cfg
