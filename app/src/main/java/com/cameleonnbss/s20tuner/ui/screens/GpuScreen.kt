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
fun GpuScreen(vm: TunerViewModel) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val dev = vm.ui.collectAsStateWithLifecycle().value.device
    val st by vm.status.collectAsStateWithLifecycle()

    val gpuFreqs = dev.gpuFreqs.ifEmpty { listOf(305000, 457000, 610000, 762000, 862000) }
    val govs = dev.gpuGovs.ifEmpty { listOf("simple_ondemand", "performance", "powersave") }

    ScreenScaffold("GPU", if (dev.soc == "exynos990") "Mali-G77 (x1s)" else "Adreno 650 (y2s)") {

        SectionCard("Live") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatTile("Freq", "${st.gpuFreq / 1000} MHz", Modifier.weight(1f))
                StatTile("Busy", "${st.gpuBusy}%", Modifier.weight(1f))
                StatTile("Max", "${st.gpuMax / 1000} MHz", Modifier.weight(1f))
            }
            MiniGraph(vm.gpuHist.value.map { it.toFloat() })
        }

        SectionCard("Governor") {
            ChoiceRow(govs, cfg.gpuGovernor.takeIf { it.isNotBlank() }) { g ->
                vm.setConfig(cfg.copy(gpuGovernor = g))
            }
        }

        SectionCard("Frequencies") {
            LabeledSlider(
                "Min GPU freq", (cfg.gpuMinFreq.takeIf { it > 0 }?.toFloat() ?: gpuFreqs.first().toFloat()),
                { vm.setConfig(cfg.copy(gpuMinFreq = it.toInt())) },
                gpuFreqs.first().toFloat()..gpuFreqs.last().toFloat(),
                format = { "${(it / 1000).toInt()} MHz" }
            )
            LabeledSlider(
                "Max GPU freq (OC)", (cfg.gpuMaxFreq.takeIf { it > 0 }?.toFloat() ?: gpuFreqs.last().toFloat()),
                { vm.setConfig(cfg.copy(gpuMaxFreq = it.toInt())) },
                gpuFreqs.first().toFloat()..gpuFreqs.last().toFloat(),
                format = { "${(it / 1000).toInt()} MHz" }
            )
        }

        SectionCard("Undervolt") {
            LabeledSlider(
                "Voltage offset", cfg.gpuUvOffset.toFloat(),
                { vm.setConfig(cfg.copy(gpuUvOffset = it.toInt())) },
                -100f..0f, steps = 19,
                format = { "${it.toInt()} mV" }
            )
            Text("Applies to gpu_volt_offset (Mali) or gx offsets (Adreno) when the kernel exposes them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        ActionButton("Apply GPU", modifier = Modifier.fillMaxWidth(), primary = true) { vm.applyConfig() }
    }
}
