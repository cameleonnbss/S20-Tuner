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
    val gpuGov: String = "",
    val gpuGovs: List<String> = emptyList(),
    val uv: Int = 0,
    val busy: Boolean = false,
    // clusters (dynamic, little -> prime)
    val labels: List<String> = emptyList(),
    val cur: List<Int> = emptyList(),
    val mx: List<Int> = emptyList(),
    val gpu: Int = 0,
    val gmax: Int = 0,
    val gpuCeil: Int = 0,
    val ceilings: List<Int> = emptyList(),
    val temp: Float = 0f,
    val load: Float = 0f
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _s = MutableStateFlow(Ui())
    val s: StateFlow<Ui> = _s

    private var probe = Core.Probe()

    private fun labelFor(maxKHz: Int): String = when {
        maxKHz >= 2600000 -> "M5"
        maxKHz >= 2200000 -> "A76"
        else -> "A55"
    }

    init {
        viewModelScope.launch {
            val (ok, _) = withContext(Dispatchers.IO) { Core.su("id -u") }
            _s.value = _s.value.copy(root = ok)
            if (ok) {
                probe = withContext(Dispatchers.IO) { Core.probe() }
                val labels = probe.pols.map { p -> labelFor(probe.tables[p]?.maxOrNull() ?: 0) }
                val n = probe.pols.size
                val saved = withContext(Dispatchers.IO) { Core.currentCfg() }
                _s.value = if (saved != null) _s.value.copy(
                    device = probe.device, hasVdd = probe.hasVdd, gpuGovs = probe.gpuGovs,
                    ceilings = probe.ceilings, gpuCeil = probe.gpuCeil,
                    labels = labels, cur = List(n) { 0 }, mx = List(n) { 0 },
                    minPct = saved.minPct, maxPct = saved.maxPct, gov = saved.gov,
                    gpuMaxPct = saved.gpuMaxPct, gpuGov = saved.gpuGov, uv = saved.uv
                ) else _s.value.copy(
                    device = probe.device, hasVdd = probe.hasVdd, gpuGovs = probe.gpuGovs,
                    ceilings = probe.ceilings, gpuCeil = probe.gpuCeil,
                    labels = labels, cur = List(n) { 0 }, mx = List(n) { 0 }
                )
                poll()
            }
        }
    }

    private fun poll() = viewModelScope.launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val (_, out) = Core.su(Core.pollScript(probe), 8000)
                val m = HashMap<String, String>()
                out.lines().forEach { l ->
                    val i = l.indexOf(' ')
                    if (i > 0) m[l.take(i)] = l.substring(i + 1).trim()
                }
                val n = probe.pols.size
                _s.value = _s.value.copy(
                    cur = (0 until n).map { m["L$it"]?.toIntOrNull() ?: 0 },
                    mx = (0 until n).map { m["MX$it"]?.toIntOrNull() ?: 0 },
                    gpu = m["GPUF"]?.toIntOrNull() ?: 0,
                    gmax = m["GMAX"]?.toIntOrNull() ?: 0,
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
