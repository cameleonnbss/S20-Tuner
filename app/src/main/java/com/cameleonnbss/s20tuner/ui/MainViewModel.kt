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
    val rate: Int = 0,
    val last: String = "",
    val uv: Int = 0,
    val hasVdd: Boolean = false,
    val l0: Int = 0, val l4: Int = 0, val l7: Int = 0,
    val gpu: Int = 0,
    val temp: Float = 0f,
    val load: Float = 0f,
    val busy: Boolean = false
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
                val rate = withContext(Dispatchers.IO) { Core.readRate() }
                _s.value = _s.value.copy(
                    device = probe.device,
                    hasVdd = probe.hasVdd,
                    rate = rate
                )
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
                    l0 = m["L0"]?.toIntOrNull() ?: 0,
                    l4 = m["L4"]?.toIntOrNull() ?: 0,
                    l7 = m["L7"]?.toIntOrNull() ?: 0,
                    gpu = m["GPUF"]?.toIntOrNull() ?: 0,
                    temp = (m["TEMP"]?.toFloatOrNull() ?: 0f) / 1000f,
                    load = m["LOAD"]?.toFloatOrNull() ?: 0f
                )
            } catch (_: Exception) {}
            delay(2000)
        }
    }

    fun applyPreset(name: String) {
        val p = Core.PRESETS.firstOrNull { it.name == name } ?: return
        if (_s.value.busy) return
        viewModelScope.launch {
            _s.value = _s.value.copy(busy = true, last = name)
            val (ok, _) = withContext(Dispatchers.IO) { Core.apply(p, probe) }
            _s.value = _s.value.copy(busy = false)
        }
    }

    fun setUv(target: Int) {
        _s.value = _s.value.copy(uv = target)
    }

    fun commitUv() {
        if (_s.value.busy) return
        viewModelScope.launch {
            _s.value = _s.value.copy(busy = true)
            withContext(Dispatchers.IO) { Core.applyUv(_s.value.uv) }
            _s.value = _s.value.copy(busy = false)
        }
    }

    fun setRate(hz: Int) {
        _s.value = _s.value.copy(rate = hz)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Core.setRate(hz) }
        }
    }
}
