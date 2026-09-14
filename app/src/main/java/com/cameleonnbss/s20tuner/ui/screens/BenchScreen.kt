package com.cameleonnbss.s20tuner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cameleonnbss.s20tuner.core.BenchResult
import com.cameleonnbss.s20tuner.ui.*
import com.cameleonnbss.s20tuner.ui.TunerViewModel

@Composable
fun BenchScreen(vm: TunerViewModel) {
    val history by vm.benchHistory.collectAsStateWithLifecycle()
    val running by vm.benchRunning.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshBenchHistory() }

    val last = history.lastOrNull()
    val prev = history.dropLast(1).lastOrNull()

    ScreenScaffold("Benchmark", "Compare before / after tuning (same conditions!)") {

        SectionCard("Run") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(if (running) "Running…" else "Run benchmark", primary = !running) { vm.runBenchmark(if (history.isEmpty()) "before" else "run ${history.size + 1}") }
            }
            Text("CPU: SHA-256 throughput · MEM: copy bandwidth · IO: cache write/read MB/s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        if (last != null && prev != null) {
            SectionCard("Last vs previous") {
                compareRow("CPU ops/s", prev.cpuScore, last.cpuScore)
                compareRow("MEM MB/s", prev.memScore, last.memScore)
                compareRow("IO MB/s", prev.ioScore, last.ioScore)
            }
        }

        SectionCard("History (${history.size})") {
            history.asReversed().take(12).forEach { r: BenchResult ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(r.timestamp))}  ${r.label}", style = MaterialTheme.typography.bodySmall)
                    Text("CPU ${r.cpuScore} · MEM ${r.memScore} · IO ${r.ioScore}", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
            }
        }
    }
}

@Composable
private fun compareRow(label: String, before: Int, after: Int) {
    val diff = after - before
    val pct = if (before > 0) diff * 100f / before else 0f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(
            "$before → $after  (${"%+.1f".format(pct)}%)",
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = if (diff >= 0) Green else Red
        )
    }
}
