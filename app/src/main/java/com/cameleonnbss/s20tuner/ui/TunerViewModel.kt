package com.cameleonnbss.s20tuner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cameleonnbss.s20tuner.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LiveStatus(
    val cpuFreqs: List<Int> = listOf(0, 0, 0),
    val cpuMaxes: List<Int> = listOf(0, 0, 0),
    val gpuFreqMhz: Int = 0, val gpuBusy: Int = 0, val gpuMaxMhz: Int = 0,
    val cpuTemp: Float = 0f, val battTemp: Float = 0f, val battPct: Int = 0,
    val load: Float = 0f, val autoState: String = ""
)

data class UiState(
    val probing: Boolean = true,
    val rootOk: Boolean = false,
    val device: DeviceInfo = DeviceInfo(),
    val autoRunning: Boolean = false,
    val bootRestore: Boolean = false,
    val toast: String = "",
    val logs: List<String> = emptyList()
)

class TunerViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val _status = MutableStateFlow(LiveStatus())
    val status: StateFlow<LiveStatus> = _status

    val config = MutableStateFlow(OcPresets.stock)
    val autoLog = MutableStateFlow("")

    private val logLines = ArrayDeque<String>()
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            log("990 OC starting…")
            val root = withContext(Dispatchers.IO) { Shell.hasRoot() }
            _ui.value = _ui.value.copy(rootOk = root)
            if (!root) {
                log("ERROR: no root — grant superuser in Magisk")
                _ui.value = _ui.value.copy(probing = false)
                return@launch
            }
            val dev = withContext(Dispatchers.IO) { DeviceProbe.probe() }
            _ui.value = _ui.value.copy(
                device = dev,
                probing = false,
                autoRunning = withContext(Dispatchers.IO) { AutoEngine.running() },
                bootRestore = withContext(Dispatchers.IO) { AutoEngine.bootRestoreEnabled() }
            )
            log("SoC: ${dev.soc} — Exynos 990: ${if (dev.isExynos990) "yes" else "NOT detected (guarded mode)"}")
            log("Clusters: ${dev.tables.entries.joinToString { "${it.key.substringAfterLast('/')}:${it.value.size} freqs" }}")
            log("vdd table: ${if (dev.hasVdd) "available (undervolt ON)" else "not exposed (UV needs a kernel like Masonic)"}")
            startPolling()
        }
    }

    private fun log(s: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        synchronized(logLines) {
            logLines.addLast("[$ts] $s")
            while (logLines.size > 200) logLines.removeFirst()
            _ui.value = _ui.value.copy(logs = logLines.toList())
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val r = Shell.exec(Sysfs.pollScript(), 10000)
                    if (r.ok) parse(r.out)
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    private fun parse(out: String) {
        val map = HashMap<String, String>()
        for (line in out.lines()) {
            val i = line.indexOf(' ')
            if (i > 0) map[line.substring(0, i)] = line.substring(i + 1).trim()
        }
        fun f(k: String) = map[k]?.toFloatOrNull() ?: 0f
        fun i(k: String) = map[k]?.toIntOrNull() ?: 0
        _status.value = LiveStatus(
            cpuFreqs = listOf(i("L0"), i("L4"), i("L7")),
            cpuMaxes = listOf(i("MAX0"), i("MAX4"), i("MAX7")),
            gpuFreqMhz = i("GPUF") / 1000,
            gpuBusy = i("GPUB").let { if (it in 1..100) it else 0 },
            gpuMaxMhz = i("GMAXC") / 1000,
            cpuTemp = f("T7") / 1000f,
            battTemp = f("TBAT") / 10f,
            battPct = i("BPCT"),
            load = f("LOAD"),
            autoState = map["OCST"] ?: ""
        )
    }

    fun setConfig(c: OcConfig) { config.value = c }

    fun apply(c: OcConfig = config.value) {
        viewModelScope.launch(Dispatchers.IO) {
            val dev = _ui.value.device
            val script = ApplyEngine.buildScript(c, dev.tables, dev.gpuTable)
            val r = Shell.exec(script, 20000)
            if (r.out.contains("APPLIED")) {
                log("Applied: min=${c.clusterMinKhz} max=${c.clusterMaxKhz} cpuUV=${c.cpuUvDelta} gpuUV=${c.gpuUvDelta}")
                _ui.value = _ui.value.copy(toast = "Applied ✓")
                if (_ui.value.bootRestore) {
                    AutoEngine.syncBootApply(dev.tables, dev.gpuTable, c.toJson())
                }
            } else {
                log("Apply failed: ${r.err.takeLast(100)}")
                _ui.value = _ui.value.copy(toast = "Apply failed")
            }
        }
    }

    fun applyPreset(name: String) {
        val p = OcPresets.all[name] ?: return
        setConfig(p)
        apply(p)
        _ui.value = _ui.value.copy(toast = "$name preset applied")
    }

    fun setAuto(on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val dev = _ui.value.device
            val msg = AutoEngine.setAuto(getApplication(), on, dev.tables, dev.gpuTable)
            log(msg)
            _ui.value = _ui.value.copy(toast = msg, autoRunning = on && msg.contains("started"))
        }
    }

    fun setBootRestore(on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val dev = _ui.value.device
            val msg = AutoEngine.setBootRestore(on, config.value.toJson())
            if (on) AutoEngine.syncBootApply(dev.tables, dev.gpuTable, config.value.toJson())
            log(msg)
            _ui.value = _ui.value.copy(toast = msg, bootRestore = on && msg.contains("enabled"))
        }
    }

    fun readAutoLog() {
        viewModelScope.launch(Dispatchers.IO) { autoLog.value = AutoEngine.tailLog() }
    }

    fun consumeToast() { _ui.value = _ui.value.copy(toast = "") }
}
