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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    !s.root -> "no root"
                    s.device.isNotBlank() -> "Exynos 990 · ${s.device}"
                    else -> "checking device…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // refresh rate
        Section("Refresh rate") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RateButton("60 Hz", s.rate == 60, Modifier.weight(1f)) { vm.setRate(60) }
                RateButton("120 Hz", s.rate == 120, Modifier.weight(1f)) { vm.setRate(120) }
            }
        }

        // presets
        Section("Presets") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PresetCard("Overclock", "max clocks, −10 mV", Modifier.weight(1f), s.last == "Overclock") { vm.applyPreset("Overclock") }
                PresetCard("Stock", "default everything", Modifier.weight(1f), s.last == "Stock") { vm.applyPreset("Stock") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PresetCard("Underclock", "cool & efficient", Modifier.weight(1f), s.last == "Underclock") { vm.applyPreset("Underclock") }
                PresetCard("Sleep", "deep idle caps", Modifier.weight(1f), s.last == "Sleep") { vm.applyPreset("Sleep") }
            }
        }

        // custom undervolt
        Section("Undervolt") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CPU voltage shift", style = MaterialTheme.typography.bodyMedium)
                Text("${s.uv / 1000} mV", color = Green, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = s.uv.toFloat(),
                onValueChange = { vm.setUv((it / 2500).toInt() * 2500) },
                onValueChangeFinished = { vm.commitUv() },
                valueRange = -60000f..0f
            )
            if (!s.hasVdd) {
                Text(
                    "needs a kernel with vdd_levels (Masonic, Ragnarøk…)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // live
        Section("Live") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("A55", "${s.l0 / 1000}", "MHz", Modifier.weight(1f))
                Tile("A76", "${s.l4 / 1000}", "MHz", Modifier.weight(1f))
                Tile("M5", "${s.l7 / 1000}", "MHz", Modifier.weight(1f))
                Tile("GPU", "${s.gpu / 1000}", "MHz", Modifier.weight(1f))
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
fun RateButton(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Green else GreenDark,
            contentColor = Color.White
        )
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
