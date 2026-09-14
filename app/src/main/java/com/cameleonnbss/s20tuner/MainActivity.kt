package com.cameleonnbss.s20tuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cameleonnbss.s20tuner.core.OcPresets
import com.cameleonnbss.s20tuner.ui.Green
import com.cameleonnbss.s20tuner.ui.S20TunerTheme
import com.cameleonnbss.s20tuner.ui.TunerViewModel

class MainActivity : ComponentActivity() {
    private val vm: TunerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            S20TunerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OcApp(vm)
                }
            }
        }
    }
}

@Composable
fun OcApp(vm: TunerViewModel) {
    val ui by vm.ui.collectAsState()
    val st by vm.status.collectAsState()
    val cfg by vm.config.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ui.toast) {
        if (ui.toast.isNotEmpty()) {
            snackbar.showSnackbar(ui.toast)
            vm.consumeToast()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("990 OC", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Green)
            Spacer(Modifier.width(10.dp))
            if (ui.probing) Text("probing…", style = MaterialTheme.typography.bodySmall)
            else if (!ui.rootOk) Text("NO ROOT", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            else if (!ui.device.isExynos990) Text("Exynos 990 not detected", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            else Text("Exynos 990 · ${ui.device.kernel}", style = MaterialTheme.typography.bodySmall, color = Green)
        }

        // ---- live status ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveTile("A55", "${(st.cpuFreqs[0]) / 1000}", "MHz", Modifier.weight(1f))
            LiveTile("A76", "${st.cpuFreqs[1] / 1000}", "MHz", Modifier.weight(1f))
            LiveTile("M5", "${st.cpuFreqs[2] / 1000}", "MHz", Modifier.weight(1f))
            LiveTile("GPU", "${st.gpuFreqMhz}", "MHz", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveTile("CPU temp", "%.0f".format(st.cpuTemp), "°C", Modifier.weight(1f))
            LiveTile("Battery", "${st.battPct}", "% · %.1f°C".format(st.battTemp), Modifier.weight(1f))
            LiveTile("Load", "%.0f".format(st.load * 100), "%", Modifier.weight(1f))
            LiveTile("Auto", st.autoState.uppercase(), "", Modifier.weight(1f), accent = if (st.autoState == "oc") Green else Color.Gray)
        }

        // ---- presets ----
        Text("Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OcPresets.all.entries.take(2).forEach { (name, _) ->
                PresetButton(name, Modifier.weight(1f)) { vm.applyPreset(name) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OcPresets.all.entries.drop(2).forEach { (name, _) ->
                PresetButton(name, Modifier.weight(1f)) { vm.applyPreset(name) }
            }
        }

        // ---- custom OC ----
        Text("Custom tuning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val dev = ui.device
                val names = listOf("A55 little", "A76 big", "M5 prime")
                names.forEachIndexed { i, n ->
                    val table = dev.tables[com.cameleonnbss.s20tuner.core.Sysfs.POLICIES[i]] ?: listOf(500000, 2800000)
                    val maxK = table.maxOrNull() ?: 2800000
                    val minK = table.minOrNull() ?: 500000
                    val curMin = (cfg.clusterMinKhz.getOrNull(i) ?: -1).let { if (it > 0) it else minK }
                    val curMax = (cfg.clusterMaxKhz.getOrNull(i) ?: -1).let { if (it > 0) it else maxK }
                    FreqSlider(n, curMin, minK, maxK) { v ->
                        val l = cfg.clusterMinKhz.toMutableList(); l[i] = v; vm.setConfig(cfg.copy(clusterMinKhz = l))
                    }
                    FreqSlider("$n max", curMax, minK, maxK) { v ->
                        val l = cfg.clusterMaxKhz.toMutableList(); l[i] = v; vm.setConfig(cfg.copy(clusterMaxKhz = l))
                    }
                }

                // GPU max
                val gTable = dev.gpuTable.ifEmpty { listOf(315000, 770000) }
                val gMaxK = gTable.maxOrNull() ?: 770000
                val gMinK = gTable.minOrNull() ?: 315000
                val gCurMax = if (cfg.gpuMaxMhz > 0) cfg.gpuMaxMhz * 1000 else gMaxK
                FreqSlider("GPU max", gCurMax, gMinK, gMaxK) { v ->
                    vm.setConfig(cfg.copy(gpuMaxMhz = v / 1000))
                }

                // undervolts
                UvSlider("CPU undervolt", cfg.cpuUvDelta) { vm.setConfig(cfg.copy(cpuUvDelta = it)) }
                UvSlider("GPU undervolt", cfg.gpuUvDelta) { vm.setConfig(cfg.copy(gpuUvDelta = it)) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.apply() }) { Text("Apply custom") }
                    OutlinedButton(onClick = { vm.setConfig(OcPresets.stock); vm.apply(OcPresets.stock) }) { Text("Reset to stock") }
                }
                if (!dev.hasVdd) {
                    Text("vdd_levels not exposed by this kernel — CPU undervolt needs a kernel like Masonic/Ragnarøk.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
        }

        // ---- automation ----
        Text("Automation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoRow("Auto mode", "load → OC · temp > 40°C → UC · screen off → sleep",
                    ui.autoRunning) { vm.setAuto(it) }
                AutoRow("Apply at boot", "re-applies the current config after every reboot",
                    ui.bootRestore) { vm.setBootRestore(it) }
                OutlinedButton(onClick = { vm.readAutoLog() }) { Text("Read auto log") }
                if (vm.autoLog.value.isNotBlank()) {
                    Text(vm.autoLog.value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.height(120.dp))
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
    SnackbarHost(snackbar)
}

@Composable
fun LiveTile(label: String, value: String, unit: String, modifier: Modifier = Modifier, accent: Color = Green) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = accent, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun PresetButton(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(14.dp)) {
        Text(name, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FreqSlider(label: String, cur: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${cur / 1000} MHz", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Green, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = cur.toFloat(),
            onValueChange = { onValue((it / 25000).toInt() * 25000) },  // 25 MHz steps
            valueRange = min.toFloat()..max.toFloat()
        )
    }
}

@Composable
fun UvSlider(label: String, cur: Int, onValue: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${cur / 1000} mV", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = if (cur < 0) Green else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = cur.toFloat(),
            onValueChange = { onValue((it / 2500).toInt() * 2500) },  // 2.5 mV steps
            valueRange = -60000f..0f
        )
    }
}

@Composable
fun AutoRow(label: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
