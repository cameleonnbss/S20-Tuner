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
fun IoScreen(vm: TunerViewModel) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val dev = vm.ui.collectAsStateWithLifecycle().value.device

    val scheds = dev.ioSchedulers.ifEmpty { listOf("mq-deadline", "bfq", "kyber", "none") }

    ScreenScaffold("Storage / IO", "I/O scheduler and read-ahead for UFS 3.0") {

        SectionCard("I/O scheduler") {
            Text("Detected: ${if (dev.ioSchedulers.isNotEmpty()) dev.ioSchedulers.joinToString(" / ") else "generic list"}", style = MaterialTheme.typography.bodySmall)
            ChoiceRow(scheds, cfg.ioScheduler.takeIf { it.isNotBlank() }) { s ->
                vm.setConfig(cfg.copy(ioScheduler = s))
            }
        }

        SectionCard("Read-ahead") {
            LabeledSlider(
                "read_ahead_kb", (cfg.readAheadKb.takeIf { it > 0 }?.toFloat() ?: 512f),
                { vm.setConfig(cfg.copy(readAheadKb = it.toInt())) },
                64f..2048f, steps = 9,
                format = { "${it.toInt()} KB" }
            )
            Text("Sequential reads benefit from 512–1024 KB on UFS; smaller = better random IO.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        SectionCard("Wakelock suspend (idle drain)") {
            Text("See the Battery tab for the wakelock/package suspend list (applied together).", style = MaterialTheme.typography.bodySmall)
        }

        ActionButton("Apply IO settings", modifier = Modifier.fillMaxWidth(), primary = true) { vm.applyConfig() }
    }
}
