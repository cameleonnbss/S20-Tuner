package com.cameleonnbss.s20tuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cameleonnbss.s20tuner.ui.*
import com.cameleonnbss.s20tuner.ui.screens.*
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Cpu("CPU", Icons.Filled.Memory),
    Gpu("GPU", Icons.Filled.Speed),
    Ram("RAM", Icons.Filled.Storage),
    Display("Display", Icons.Filled.Monitor),
    Battery("Battery", Icons.Filled.BatteryChargingFull),
    Io("Storage/IO", Icons.Filled.Save),
    Network("Network", Icons.Filled.Wifi),
    Debloat("Debloat", Icons.Filled.DeleteSweep),
    Profiles("Profiles", Icons.Filled.Tune),
    Bench("Benchmark", Icons.Filled.PlayCircleOutline),
    Logs("Logs", Icons.Filled.Terminal)
}

class MainActivity : ComponentActivity() {
    private val vm: TunerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            S20TunerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm)
                }
            }
        }
    }
}

@Composable
fun AppRoot(vm: TunerViewModel) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Dashboard) }

    LaunchedEffect(ui.toast) {
        if (ui.toast.isNotEmpty()) {
            snackbar.showSnackbar(ui.toast, duration = SnackbarDuration.Short)
            vm.consumeToast()
        }
    }

    val compact = LocalConfiguration.current.screenWidthDp < 640

    if (compact) {
        // phones: top tabs
        Column {
            TabRow(selectedTabIndex = screen.ordinal) {
                Screen.entries.forEach { s ->
                    Tab(
                        selected = screen == s,
                        onClick = { screen = s },
                        text = { Text(s.title, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(s.icon, null, Modifier.size(18.dp)) }
                    )
                }
            }
            Box(Modifier.fillMaxSize()) { ScreenContent(screen, vm) }
        }
    } else {
        // landscape / tablet: left rail (permanent navigation drawer feel)
        Row {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                header = {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("S20", color = Green, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                        Text("Tuner", color = Green, style = MaterialTheme.typography.labelSmall)
                    }
                }
            ) {
                Screen.entries.forEach { s ->
                    NavigationRailItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = { Icon(s.icon, null) },
                        label = { Text(s.title, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Box(Modifier.fillMaxSize().weight(1f)) { ScreenContent(screen, vm) }
        }
    }

    SnackbarHost(snackbar)
}

@Composable
fun ScreenContent(screen: Screen, vm: TunerViewModel) {
    when (screen) {
        Screen.Dashboard -> DashboardScreen(vm)
        Screen.Cpu -> CpuScreen(vm)
        Screen.Gpu -> GpuScreen(vm)
        Screen.Ram -> RamScreen(vm)
        Screen.Display -> DisplayScreen(vm)
        Screen.Battery -> BatteryScreen(vm)
        Screen.Io -> IoScreen(vm)
        Screen.Network -> NetworkScreen(vm)
        Screen.Debloat -> DebloatScreen(vm)
        Screen.Profiles -> ProfilesScreen(vm)
        Screen.Bench -> BenchScreen(vm)
        Screen.Logs -> LogsScreen(vm)
    }
}
