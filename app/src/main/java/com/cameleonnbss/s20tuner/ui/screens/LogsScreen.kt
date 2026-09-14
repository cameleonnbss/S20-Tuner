package com.cameleonnbss.s20tuner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cameleonnbss.s20tuner.ui.TunerViewModel
import com.cameleonnbss.s20tuner.ui.ActionButton
import com.cameleonnbss.s20tuner.ui.ScreenScaffold

@Composable
fun LogsScreen(vm: TunerViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var dmesg by remember { mutableStateOf("") }
    var logcat by remember { mutableStateOf("") }

    ScreenScaffold("Logs", "App events, kernel ring buffer and logcat") {
        TabRow(selectedTabIndex = tab) {
            listOf("App", "dmesg (kernel)", "logcat").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> LogBox(ui.logs.joinToString("\n"))
            1 -> Column {
                ActionButton("Read dmesg") { vm.readKernelLog { dmesg = it } }
                LogBox(dmesg)
            }
            2 -> Column {
                ActionButton("Read logcat") { vm.runLogcat { logcat = it } }
                LogBox(logcat)
            }
        }
    }
}

@Composable
private fun LogBox(text: String) {
    if (text.isBlank()) {
        Text("— empty —", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        return
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).height(360.dp)
    )
}
