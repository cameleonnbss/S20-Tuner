package com.cameleonnbss.s20tuner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cameleonnbss.s20tuner.core.Core
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Ui(
    val root: Boolean = false,
    val device: String = "",
    val hasVdd: Boolean = false,
    val last: String = "",
    // manual config being edited
    val minPct: List<Int> = listOf(-1, -1, -1),
    val maxPct: List<Int> = listOf(100, 100, 100),
    val gov: String = "schedutil",
    val gpuMaxPct: Int = 100,
    val gpuGov: String = "simple_ondemand",
    val uv: Int = 0,
    val busy: Boolean = false,
    // live values
    val l0: Int = 0, val mx0: Int = 0,
    val l4: Int = 0, val mx4: Int = 0,
    val l7: Int = 0, val mx7: Int = 0,
    val gpu: Int = 0, val gmax: Int = 0,
    val temp: Float = 0f,
    val load: Float = 0f
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _s = MutableStateFlow(Ui())
    val s: StateFlow<Ui> = _s

    private var probe = Core.Probe()

    init {
        viewModelScope.launch {
            val (ok, _) = withContext(Dispatchers.IO) { Core.su("id -u") }
            _s.value = _s.value.copy(root = ok)
            if (ok) {
                probe = withContext(Dispatchers.IO) { Core.probe() }
                val saved = withContext(Dispatchers.IO) { Core.currentCfg() }
                _s.value = if (saved != null) _s.value.copy(
                    device = probe.device, hasVdd = probe.hasVdd,
                    minPct = saved.minPct, maxPct = saved.maxPct, gov = saved.gov,
                    gpuMaxPct = saved.gpuMaxPct, gpuGov = saved.gpuGov, uv = saved.uv
                ) else _s.value.copy(device = probe.device, hasVdd = probe.hasVdd)
                poll()
            }
        }
    }

    private fun poll() = viewModelScope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val (_, out) = Core.su(Core.pollScript(), 8000)
                val m = HashMap<String, String>()
                out.lines().forEach { l ->
                    val i = l.indexOf(' ')
                    if (i > 0) m[l.take(i)] = l.substring(i + 1).trim()
                }
                _s.value = _s.value.copy(
                    l0 = m["L0"]?.toIntOrNull() ?: 0, mx0 = m["MX0"]?.toIntOrNull() ?: 0,
                    l4 = m["L4"]?.toIntOrNull() ?: 0, mx4 = m["MX4"]?.toIntOrNull() ?: 0,
                    l7 = m["L7"]?.toIntOrNull() ?: 0, mx7 = m["MX7"]?.toIntOrNull() ?: 0,
                    gpu = m["GPUF"]?.toIntOrNull() ?: 0, gmax = m["GMAX"]?.toIntOrNull() ?: 0,
                    temp = (m["TEMP"]?.toFloatOrNull() ?: 0f) / 1000f,
                    load = m["LOAD"]?.toFloatOrNull() ?: 0f
                )
            } catch (_: Exception) {}
            delay(1500)
            if (!isActive) return@launch
        }
    }

    private fun cfg() = Core.Cfg(
        _s.value.minPct, _s.value.maxPct, _s.value.gov,
        _s.value.gpuMaxPct, _s.value.gpuGov, _s.value.uv
    )

    private suspend fun doApply(c: Core.Cfg, tag: String) {
        if (_s.value.busy) return
        _s.value = _s.value.copy(busy = true, last = tag)
        withContext(Dispatchers.IO) { Core.applyCfg(c, probe) }
        _s.value = _s.value.copy(busy = false)
    }

    fun applyPreset(name: String) {
        viewModelScope.launch {
            val c = when (name) {
                "BEST" -> Core.BEST
                else -> Core.PRESETS[name] ?: return@launch
            }
            _s.value = _s.value.copy(
                minPct = c.minPct, maxPct = c.maxPct, gov = c.gov,
                gpuMaxPct = c.gpuMaxPct, gpuGov = c.gpuGov, uv = c.uv
            )
            doApply(c, name)
        }
    }

    fun applyCustom() = viewModelScope.launch { doApply(cfg(), "Custom") }

    fun setMin(i: Int, v: Int) {
        val m = _s.value.minPct.toMutableList(); m[i] = v
        _s.value = _s.value.copy(minPct = m)
    }

    fun setMax(i: Int, v: Int) {
        val m = _s.value.maxPct.toMutableList(); m[i] = v
        _s.value = _s.value.copy(maxPct = m)
    }

    fun setGov(g: String) { _s.value = _s.value.copy(gov = g) }
    fun setGpuMax(v: Int) { _s.value = _s.value.copy(gpuMaxPct = v) }
    fun setGpuGov(g: String) { _s.value = _s.value.copy(gpuGov = g) }
    fun setUv(target: Int) { _s.value = _s.value.copy(uv = target) }
}
