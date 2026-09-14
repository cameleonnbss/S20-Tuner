package com.cameleonnbss.s20tuner.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cameleonnbss.s20tuner.core.*
import com.cameleonnbss.s20tuner.data.DebloatEntry
import com.cameleonnbss.s20tuner.data.ProfileStore
import com.cameleonnbss.s20tuner.model.Presets
import com.cameleonnbss.s20tuner.model.Profile
import com.cameleonnbss.s20tuner.model.TunerConfig
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
    val gpuFreq: Int = 0, val gpuBusy: Int = 0, val gpuMax: Int = 0,
    val tempCpu: Float = 0f, val tempGpu: Float = 0f, val tempBat: Float = 0f,
    val battPct: Int = 0, val battCharging: String = "", val battCurrentMa: Int = 0, val battVoltageUv: Int = 0,
    val memTotalKb: Long = 0, val memAvailKb: Long = 0,
    val swapTotalKb: Long = 0, val swapFreeKb: Long = 0, val zramUsedKb: Long = 0,
    val fpsText: String = "", val refreshRate: Int = 0,
    val thermalConfig: String = "",
    val load: Float = 0f
)

data class UiState(
    val probing: Boolean = true,
    val rootOk: Boolean = false,
    val device: DeviceInfo = DeviceInfo(),
    val bootPersistEnabled: Boolean = false,
    val toast: String = "",
    val logs: List<String> = emptyList(),
    val lastAppliedMs: Long = 0
)

class TunerViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val _status = MutableStateFlow(LiveStatus())
    val status: StateFlow<LiveStatus> = _status

    val config = MutableStateFlow(TunerConfig())

    val profiles = MutableStateFlow<List<Profile>>(emptyList())

    private var pollJob: Job? = null
    private val logLines = ArrayDeque<String>()

    /** rolling history for graphs */
    val cpuHist = MutableStateFlow<List<Int>>(emptyList())
    val gpuHist = MutableStateFlow<List<Int>>(emptyList())
    val tempHist = MutableStateFlow<List<Float>>(emptyList())
    val battHist = MutableStateFlow<List<Int>>(emptyList())

    init {
        viewModelScope.launch {
            log("S20 Tuner starting…")
            val root = withContext(Dispatchers.IO) { Shell.hasRoot() }
            _ui.value = _ui.value.copy(rootOk = root)
            if (!root) {
                log("ERROR: no root. Grant Magisk superuser and retry.")
                _ui.value = _ui.value.copy(probing = false)
                return@launch
            }
            log("Root OK — Magisk ${Shell.magiskVersion()}")
            val dev = withContext(Dispatchers.IO) { DeviceProbe.probe() }
            _ui.value = _ui.value.copy(device = dev, probing = false, bootPersistEnabled = withContext(Dispatchers.IO) { BootInstaller.status() })
            log("Device: ${dev.model} (${dev.codename}) — ${if (dev.isSupported) "supported" else "NOT officially supported, nodes guarded"}")
            log("CPU policies: ${dev.cpuPolicies.size} — GPU: ${if (dev.soc == "exynos990") "Mali-G77" else "Adreno 650"}")
            refreshProfiles()
            refreshAuto()
            startPolling()
        }
    }

    private fun log(s: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        synchronized(logLines) {
            logLines.addLast("[$ts] $s")
            while (logLines.size > 400) logLines.removeFirst()
            _ui.value = _ui.value.copy(logs = logLines.toList())
        }
    }

    fun clearLogs() = synchronized(logLines) { logLines.clear(); _ui.value = _ui.value.copy(logs = emptyList()) }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val r = Shell.exec(Sysfs.pollScript(), 10000)
                    if (r.ok) parseStatus(r.out)
                } catch (_: Exception) {}
                delay(1500)
            }
        }
    }

    private fun parseStatus(out: String) {
        val map = HashMap<String, String>()
        for (line in out.lines()) {
            val i = line.indexOf(' ')
            if (i > 0) map[line.substring(0, i)] = line.substring(i + 1).trim()
        }
        fun f(k: String) = map[k]?.toFloatOrNull() ?: 0f
        fun i(k: String) = map[k]?.toIntOrNull() ?: 0
        fun l(k: String) = map[k]?.toLongOrNull() ?: 0L

        val st = LiveStatus(
            cpuFreqs = listOf(i("CPU0"), i("CPU4"), i("CPU6")),
            cpuMaxes = listOf(i("MAX0"), i("MAX4"), i("MAX6")),
            gpuFreq = if (i("GPUF") > 0) i("GPUF") else i("GPUKG"),
            gpuBusy = i("GPUB").let { if (it in 1..100) it else i("GPUL").let { g -> if (g in 1..100) g else 0 } },
            gpuMax = i("GMAX"),
            tempCpu = f("TEMPCPU") / 1000f,
            tempGpu = f("TEMPGPU") / 1000f,
            tempBat = f("TEMPBAT") / 10f,
            battPct = i("BATP"),
            battCharging = map["BATCHG"] ?: "",
            battCurrentMa = (f("BATIC") / 1000f).toInt(),
            battVoltageUv = i("BATV"),
            memTotalKb = l("MEMT"), memAvailKb = l("MEMA"),
            swapTotalKb = l("SWAPT"), swapFreeKb = l("SWAPF"), zramUsedKb = l("ZRAMU"),
            fpsText = map["FPS"] ?: "",
            refreshRate = i("RATE"),
            thermalConfig = map["THERM"] ?: "",
            load = map["LOAD"]?.toFloatOrNull() ?: 0f
        )
        _status.value = st
        map["AUTOST"]?.let { if (it.isNotBlank()) autoState.value = it }

        fun push(list: MutableStateFlow<List<Int>>, v: Int, max: Int = 100) {
            val cur = list.value.toMutableList()
            cur.add(v.coerceIn(0, max))
            while (cur.size > 60) cur.removeAt(0)
            list.value = cur
        }
        push(cpuHist, st.cpuFreqs.getOrNull(1) ?: 0, 3_000_000)
        push(gpuHist, st.gpuBusy, 100)
        val t = tempHist.value.toMutableList()
        t.add(st.tempBat)
        while (t.size > 60) t.removeAt(0)
        tempHist.value = t
        push(battHist, st.battPct, 100)
    }

    // ---------- applying ----------

    fun applyConfig(c: TunerConfig = config.value) {
        viewModelScope.launch(Dispatchers.IO) {
            log("Applying configuration…")
            val script = ApplyEngine.buildScript(c, _ui.value.device.cpuPolicies.ifEmpty { Sysfs.cpuPolicyPaths() })
            val r = Shell.exec(script, 30000)
            if (r.ok && r.out.contains("DONE")) {
                log("Configuration applied OK")
                _ui.value = _ui.value.copy(toast = "Applied ✓", lastAppliedMs = System.currentTimeMillis())
            } else {
                log("Apply error: ${r.err.ifBlank { Shell.lastError }}")
                _ui.value = _ui.value.copy(toast = "Apply failed — see logs")
            }
            if (c.refreshRate > 0) {
                log("Refresh rate request: ${c.refreshRate}Hz" + (if (c.lockRefreshRate) " + SF timers locked" else ""))
            }
        }
    }

    fun setConfig(c: TunerConfig) { config.value = c }

    // ---------- profiles ----------

    fun refreshProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            profiles.value = ProfileStore.list(getApplication())
        }
    }

    fun saveProfile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = Profile(name, config.value)
            ProfileStore.save(getApplication(), p)
            refreshProfiles()
            _ui.value = _ui.value.copy(toast = "Profile '$name' saved")
            log("Profile saved: $name")
        }
    }

    fun loadProfile(p: Profile) {
        config.value = p.config
        applyConfig(p.config)
        _ui.value = _ui.value.copy(toast = "Profile '${p.name}' applied")
    }

    fun deleteProfile(p: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            ProfileStore.delete(getApplication(), p.name)
            refreshProfiles()
        }
    }

    fun applyPreset(p: Profile) = loadProfile(p)

    fun exportProfiles() = viewModelScope.launch(Dispatchers.IO) {
        val msg = ProfileStore.exportAll(getApplication())
        _ui.value = _ui.value.copy(toast = msg)
    }

    fun importProfiles() = viewModelScope.launch(Dispatchers.IO) {
        val msg = ProfileStore.importAll(getApplication())
        refreshProfiles()
        _ui.value = _ui.value.copy(toast = msg)
    }

    // ---------- boot persistence ----------

    fun setBootPersist(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = BootInstaller.install(config.value.toJson(), enable)
            _ui.value = _ui.value.copy(bootPersistEnabled = enable, toast = msg)
            log(msg)
        }
    }

    // ---------- debloat ----------

    fun debloat(pkg: String, freeze: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val cmd = if (freeze)
                "pm disable-user --user 0 '$pkg' 2>&1 || pm suspend '$pkg' 2>&1"
            else
                "pm uninstall -k --user 0 '$pkg' 2>&1"
            val r = Shell.exec(cmd)
            val okLine = r.out.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
            log("Debloat ${if (freeze) "freeze" else "remove"} $pkg → $okLine")
            _ui.value = _ui.value.copy(toast = "$pkg: $okLine")
        }
    }

    fun restorePackage(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = Shell.exec("cmd package install-existing '$pkg' 2>&1")
            val okLine = r.out.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
            log("Restore $pkg → $okLine")
            _ui.value = _ui.value.copy(toast = "$pkg: $okLine")
        }
    }

    fun listThirdParty(onResult: (List<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = Shell.exec("pm list packages -3 | sed 's/package://' | sort", 15000)
            val pkgs = r.out.lines().map { it.trim() }.filter { it.isNotEmpty() }
            withContext(Dispatchers.Main) { onResult(pkgs) }
        }
    }

    // ---------- kernel log ----------

    fun readKernelLog(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = Shell.exec("dmesg | tail -200", 12000)
            withContext(Dispatchers.Main) { onResult(r.out.ifBlank { r.err }) }
        }
    }

    fun runLogcat(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = Shell.exec("logcat -d -t 200", 12000)
            withContext(Dispatchers.Main) { onResult(r.out.ifBlank { r.err }) }
        }
    }

    // ---------- benchmark ----------

    val benchHistory = MutableStateFlow<List<BenchResult>>(emptyList())
    var benchRunning = MutableStateFlow(false)

    fun runBenchmark(label: String) {
        if (benchRunning.value) return
        benchRunning.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val r = Benchmark.run(getApplication(), label)
                benchHistory.value = Benchmark.history(getApplication())
                log("Benchmark '$label': CPU=${r.cpuScore} MEM=${r.memScore} IO=${r.ioScore} MB/s")
                _ui.value = _ui.value.copy(toast = "Bench: CPU ${r.cpuScore} · MEM ${r.memScore} · IO ${r.ioScore} MB/s")
            } finally {
                benchRunning.value = false
            }
        }
    }

    fun refreshBenchHistory() {
        benchHistory.value = Benchmark.history(getApplication())
    }

    // ---------- auto OC / UC ----------

    val autoRunning = MutableStateFlow(false)
    val autoMode = MutableStateFlow("auto")
    val autoState = MutableStateFlow("init")
    val autoLog = MutableStateFlow("")

    fun refreshAuto() {
        viewModelScope.launch(Dispatchers.IO) {
            autoRunning.value = AutoEngine.running()
            autoMode.value = AutoEngine.currentMode()
            autoState.value = AutoEngine.currentState()
        }
    }

    fun setAuto(on: Boolean, mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = AutoEngine.setEnabled(getApplication(), on, mode)
            log(msg)
            _ui.value = _ui.value.copy(toast = msg)
            refreshAuto()
        }
    }

    fun changeAutoMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (autoRunning.value) {
                val msg = AutoEngine.setMode(mode)
                log(msg)
                _ui.value = _ui.value.copy(toast = msg)
                refreshAuto()
            } else {
                autoMode.value = mode
            }
        }
    }

    fun readAutoLog() {
        viewModelScope.launch(Dispatchers.IO) { autoLog.value = AutoEngine.tailLog() }
    }

    // ---------- misc helpers ----------

    fun consumeToast() { _ui.value = _ui.value.copy(toast = "") }

    fun ctx(): Context = getApplication()

    companion object {
        val presets = Presets.allPresets
    }
}
