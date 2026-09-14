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
fun RamScreen(vm: TunerViewModel) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val st by vm.status.collectAsStateWithLifecycle()
    val dev = vm.ui.collectAsStateWithLifecycle().value.device

    val zramAlgos = dev.zramAlgos.ifEmpty { listOf("lz4", "zstd", "lzo-rle", "deflate") }

    ScreenScaffold("RAM / Memory", "ZRAM compression, VM tunables and Low Memory Killer") {

        SectionCard("Live") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatTile("Total", "${st.memTotalKb / 1024} MB", Modifier.weight(1f))
                StatTile("Avail", "${st.memAvailKb / 1024} MB", Modifier.weight(1f))
                StatTile("ZRAM used", "${st.zramUsedKb / 1024} MB", Modifier.weight(1f))
            }
            val swapUsed = (st.swapTotalKb - st.swapFreeKb).coerceAtLeast(0)
            Text("Swap used: ${swapUsed / 1024} MB / ${st.swapTotalKb / 1024} MB", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("ZRAM") {
            Text("Compression algorithm", style = MaterialTheme.typography.labelSmall)
            ChoiceRow(zramAlgos, cfg.zramAlgo.takeIf { it.isNotBlank() }) { a ->
                vm.setConfig(cfg.copy(zramAlgo = a))
            }
            LabeledSlider(
                "ZRAM size (recreates swap — apps may reload)",
                (cfg.zramSizeMb.takeIf { it > 0 }?.toFloat() ?: 3072f),
                { vm.setConfig(cfg.copy(zramSizeMb = it.toInt())) },
                1024f..8192f, steps = 13,
                format = { "${it.toInt()} MB" }
            )
        }

        SectionCard("Virtual memory") {
            LabeledSlider("Swappiness", (cfg.swappiness.takeIf { it >= 0 }?.toFloat() ?: 100f), { vm.setConfig(cfg.copy(swappiness = it.toInt())) }, 0f..200f)
            LabeledSlider("dirty_ratio", (cfg.dirtyRatio.takeIf { it >= 0 }?.toFloat() ?: 20f), { vm.setConfig(cfg.copy(dirtyRatio = it.toInt())) }, 5f..60f)
            LabeledSlider("dirty_background_ratio", (cfg.dirtyBgRatio.takeIf { it >= 0 }?.toFloat() ?: 5f), { vm.setConfig(cfg.copy(dirtyBgRatio = it.toInt())) }, 1f..30f)
            LabeledSlider("vfs_cache_pressure", (cfg.vfsCachePressure.takeIf { it >= 0 }?.toFloat() ?: 100f), { vm.setConfig(cfg.copy(vfsCachePressure = it.toInt())) }, 50f..250f)
        }

        SectionCard("Low Memory Killer") {
            var lmk by remember(cfg.lmkMinfree) { mutableStateOf(cfg.lmkMinfree.ifBlank { "25600,51200,76800,102400,128000,153600" }) }
            OutlinedTextField(
                value = lmk, onValueChange = { lmk = it },
                label = { Text("minfree (6 comma-separated KB values)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Use aggressive") {
                    vm.setConfig(cfg.copy(lmkMinfree = "38400,64000,96000,128000,160000,192000"))
                }
                ActionButton("Use lenient") {
                    vm.setConfig(cfg.copy(lmkMinfree = "12800,25600,38400,64000,96000,128000"))
                }
            }
            ActionButton("Save LMK to config") { vm.setConfig(cfg.copy(lmkMinfree = lmk)) }
        }

        ActionButton("Apply memory settings", modifier = Modifier.fillMaxWidth(), primary = true) { vm.applyConfig() }
    }
}
