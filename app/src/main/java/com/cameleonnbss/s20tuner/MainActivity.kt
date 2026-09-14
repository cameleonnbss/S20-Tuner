package com.cameleonnbss.s20tuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cameleonnbss.s20tuner.core.Core
import com.cameleonnbss.s20tuner.ui.MainViewModel
import com.cameleonnbss.s20tuner.ui.S20TunerTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            S20TunerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }
}

private val Green = Color(0xFF4CAF50)
private val GreenDark = Color(0xFF1B5E20)

@Composable
fun App(vm: MainViewModel) {
    val s by vm.s.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // header
        Column {
            Text("990 OC", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Green)
            Text(
                when {
                    !s.root -> "no root — allow it in Magisk"
                    s.device.isNotBlank() && Core.KNOWN_990.contains(s.device) -> "Exynos 990 · ${s.device}"
                    s.device.isNotBlank() -> "${s.device} — Exynos 990 models only"
                    else -> "checking device…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!s.root || (s.device.isNotBlank() && !Core.KNOWN_990.contains(s.device)))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // one-tap modes
        Section("Power mode") {
            Button(
                onClick = { vm.applyPreset("BEST") },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                enabled = s.root && !s.busy
            ) {
                Text("⚡ BEST — hold max clocks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PresetCard("Stock", "everything default", Modifier.weight(1f), s.last == "Stock") { vm.applyPreset("Stock") }
                PresetCard("Underclock", "cool & efficient", Modifier.weight(1f), s.last == "Underclock") { vm.applyPreset("Underclock") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PresetCard("Sleep", "deep idle caps", Modifier.weight(1f), s.last == "Sleep") { vm.applyPreset("Sleep") }
                PresetCard("Custom", "your tuning below", Modifier.weight(1f), s.last == "Custom") { vm.applyCustom() }
            }
        }

        // manual overclock
        Section("Custom overclock") {
            val names = if (s.labels.isEmpty()) listOf("A55", "A76", "M5") else s.labels
            names.forEachIndexed { i, n ->
                val ceil = s.ceilings.getOrElse(i) { 0 }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(n, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Green)
                    Text(
                        "min " + (if (s.minPct[i] < 0) "lowest" else "${s.minPct[i]}%") + " · max ${s.maxPct[i]}%" +
                            (if (ceil > 0) " · fw ${ceil / 1000}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Slider(
                    value = (if (s.minPct[i] < 0) 0 else s.minPct[i]).toFloat(),
                    onValueChange = { vm.setMin(i, it.toInt()) },
                    valueRange = 0f..95f,
                    enabled = s.root
                )
                Slider(
                    value = s.maxPct[i].toFloat(),
                    onValueChange = { vm.setMax(i, it.toInt().coerceIn(50, 100)) },
                    valueRange = 50f..100f,
                    enabled = s.root
                )
            }

            Text("CPU governor", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Green)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("schedutil", "performance", "powersave").forEach { g ->
                    FilterChip(
                        selected = s.gov == g,
                        onClick = { vm.setGov(g); vm.applyCustom() },
                        label = { Text(g) },
                        enabled = s.root
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("GPU max clock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Green)
                Text(
                    "${s.gpuMaxPct}%" + (if (s.gpuCeil > 0 && s.gpuCeil > (s.gmax.takeIf { it > 0 } ?: 0)) " · fw ${s.gpuCeil / 1000}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Slider(
                value = s.gpuMaxPct.toFloat(),
                onValueChange = { vm.setGpuMax(it.toInt().coerceIn(50, 100)) },
                valueRange = 50f..100f,
                enabled = s.root
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.gpuGov.isBlank(),
                    onClick = { vm.setGpuGov(""); vm.applyCustom() },
                    label = { Text("default") },
                    enabled = s.root
                )
                s.gpuGovs.forEach { g ->
                    FilterChip(
                        selected = s.gpuGov == g,
                        onClick = { vm.setGpuGov(g); vm.applyCustom() },
                        label = { Text(g) },
                        enabled = s.root
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CPU undervolt", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Green)
                Text("${s.uv / 1000} mV", color = Green, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = s.uv.toFloat(),
                onValueChange = { vm.setUv((it / 2500).toInt() * 2500) },
                valueRange = -60000f..0f,
                enabled = s.root && s.hasVdd
            )
            if (!s.hasVdd) {
                Text(
                    "undervolt needs a kernel with vdd_levels (Masonic, Ragnarøk…)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Button(
                onClick = { vm.applyCustom() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenDark, contentColor = Color.White),
                enabled = s.root && !s.busy
            ) {
                Text("Apply custom", fontWeight = FontWeight.Bold)
            }
        }

        // live
        Section("Live") {
            val tiles: List<Triple<String, Int, Int>> =
                s.labels.mapIndexed { i, l -> Triple(l, s.cur.getOrElse(i) { 0 }, s.mx.getOrElse(i) { 0 }) } +
                    listOf(Triple("GPU", s.gpu, s.gmax))
            tiles.chunked(2).forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.forEach { t ->
                        val idx = s.labels.indexOf(t.first)
                        val fwCeil = if (idx >= 0) s.ceilings.getOrElse(idx) { 0 } else s.gpuCeil
                        LiveTile(t.first, t.second, t.third, fwCeil, Modifier.weight(1f))
                    }
                    if (r.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("CPU temp", "%.0f".format(s.temp), "°C", Modifier.weight(1f))
                Tile("Load", "%.0f".format(s.load * 100), "%", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Green)
            content()
        }
    }
}

@Composable
fun PresetCard(title: String, subtitle: String, modifier: Modifier = Modifier, active: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) Green.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (active) Green else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
    }
}

@Composable
fun LiveTile(label: String, cur: Int, max: Int, fwCeil: Int, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                if (fwCeil > max && fwCeil > 0) {
                    Text("fw ${fwCeil / 1000}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB300))
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (cur > 0) "${cur / 1000}" else "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Green)
                if (max > 0) Text(" / ${max / 1000}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(" MHz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun Tile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Green)
                if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}
