package com.cameleonnbss.s20tuner.core

import java.util.concurrent.TimeUnit

object Core {
    private const val CPU = "/sys/devices/system/cpu/cpufreq"
    private val POL = listOf("$CPU/policy0", "$CPU/policy4", "$CPU/policy7") // A55 / A76 / M5
    private const val VDD = "/sys/devices/system/cpu/cpu0/cpufreq/vdd_levels"
    private const val GPU = "/sys/kernel/gpu"
    private const val UV_FILE = "/data/adb/990oc_uv"
    private const val HZ_CONF = "/data/adb/force_hz.conf"
    private const val HZ_ACTION = "/data/adb/modules/force_120hz_x1s/action.sh"

    data class Probe(
        val tables: Map<String, List<Int>> = emptyMap(),
        val gpuMax: Int = 0,
        val hasVdd: Boolean = false,
        val device: String = ""
    )

    data class Pdef(
        val name: String,
        val minPct: List<Int>,   // percent of max, -1 = table min
        val maxPct: List<Int>,
        val gov: String,
        val gpuMaxPct: Int,
        val gpuGov: String,
        val uv: Int              // microvolts, negative = undervolt
    )

    val PRESETS = listOf(
        Pdef("Overclock", listOf(55, 65, 70), listOf(100, 100, 100), "schedutil", 100, "performance", -10000),
        Pdef("Stock", listOf(-1, -1, -1), listOf(100, 100, 100), "schedutil", 100, "simple_ondemand", 0),
        Pdef("Underclock", listOf(-1, -1, -1), listOf(80, 85, 85), "schedutil", 70, "simple_ondemand", -20000),
        Pdef("Sleep", listOf(-1, -1, -1), listOf(60, 65, 70), "powersave", 50, "powersave", 0)
    )

    fun su(script: String, timeoutMs: Long = 20000): Pair<Boolean, String> = try {
        val p = ProcessBuilder("su", "-c", "sh").start()
        p.outputStream.use { it.write(script.toByteArray()); it.flush() }
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        Pair(true, out + err)
    } catch (e: Exception) {
        Pair(false, e.message ?: "su failed")
    }

    fun probe(): Probe {
        val sb = StringBuilder()
        POL.forEach { p ->
            sb.append("echo P ${p}_av\n")
            sb.append("cat $p/scaling_available_frequencies 2>/dev/null\n")
        }
        sb.append("echo P dev\ngetprop ro.product.device\n")
        sb.append("echo P gmax\ncat $GPU/gpu_max_clock 2>/dev/null\n")
        sb.append("echo P vdd\nhead -c 100 $VDD 2>/dev/null\n")
        val (_, out) = su(sb.toString(), 25000)

        val values = HashMap<String, String>()
        var cur = ""
        val acc = StringBuilder()
        for (line in out.lines()) {
            if (line.startsWith("P ")) {
                if (cur.isNotEmpty()) values[cur] = acc.toString().trim()
                acc.setLength(0)
                cur = line.substring(2).trim()
            } else {
                if (acc.isNotEmpty()) acc.append('\n')
                acc.append(line)
            }
        }
        if (cur.isNotEmpty()) values[cur] = acc.toString().trim()

        val tables = HashMap<String, List<Int>>()
        POL.forEach { p ->
            values["${p}_av"]?.let {
                tables[p] = it.split(" ").mapNotNull { n -> n.toIntOrNull() }.sorted()
            }
        }
        return Probe(
            tables = tables,
            gpuMax = values["gmax"]?.trim()?.toIntOrNull() ?: 0,
            hasVdd = (values["vdd"] ?: "").isNotBlank(),
            device = values["dev"]?.trim() ?: ""
        )
    }

    fun pollScript(): String = """
        echo L0 $(cat ${POL[0]}/scaling_cur_freq 2>/dev/null)
        echo L4 $(cat ${POL[1]}/scaling_cur_freq 2>/dev/null)
        echo L7 $(cat ${POL[2]}/scaling_cur_freq 2>/dev/null)
        echo GPUF $(cat $GPU/gpu_freq 2>/dev/null)
        echo TEMP $(cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null)
        echo RATE $(settings get system peak_refresh_rate 2>/dev/null)
        echo LOAD $(cut -d' ' -f1 /proc/loadavg 2>/dev/null)
    """.trimIndent()

    fun apply(p: Pdef, pr: Probe): Pair<Boolean, String> {
        val sb = StringBuilder()
        sb.append("mkdir -p /data/adb\n")
        POL.forEachIndexed { i, pol ->
            val t = pr.tables[pol].orEmpty()
            val mx = t.maxOrNull() ?: return@forEachIndexed
            val mn = t.minOrNull() ?: 0
            val minV = if (p.minPct[i] in 1..99) mx * p.minPct[i] / 100 else mn
            val maxV = mx * p.maxPct[i] / 100
            sb.append("[ -f $pol/scaling_governor ] && echo '${p.gov}' > $pol/scaling_governor 2>/dev/null\n")
            sb.append("[ -f $pol/scaling_max_freq ] && echo $maxV > $pol/scaling_max_freq 2>/dev/null\n")
            sb.append("[ -f $pol/scaling_min_freq ] && echo $minV > $pol/scaling_min_freq 2>/dev/null\n")
        }
        uvShift(p.uv, sb)
        if (pr.gpuMax > 0) {
            val g = pr.gpuMax * p.gpuMaxPct / 100
            sb.append("[ -f $GPU/gpu_max_clock ] && echo $g > $GPU/gpu_max_clock 2>/dev/null\n")
        }
        sb.append("[ -f $GPU/gpu_governor ] && echo '${p.gpuGov}' > $GPU/gpu_governor 2>/dev/null\n")
        return su(sb.toString())
    }

    fun applyUv(target: Int): Pair<Boolean, String> {
        val sb = StringBuilder("mkdir -p /data/adb\n")
        uvShift(target, sb)
        return su(sb.toString())
    }

    // diff-based so repeated applies never compound
    private fun uvShift(target: Int, sb: StringBuilder) {
        sb.append("CUR=\$(cat $UV_FILE 2>/dev/null); [ -z \"\$CUR\" ] && CUR=0\n")
        sb.append("SHIFT=\$(( $target - \$CUR ))\n")
        sb.append("if [ \"\$SHIFT\" -ne 0 ] && [ -f $VDD ]; then\n")
        sb.append("  awk -v d=\$SHIFT 'NF>=2{print \$1, \$2+d; next}{print}' $VDD > /data/local/tmp/.v 2>/dev/null\n")
        sb.append("  [ -s /data/local/tmp/.v ] && cat /data/local/tmp/.v > $VDD 2>/dev/null && echo $target > $UV_FILE 2>/dev/null\n")
        sb.append("  rm -f /data/local/tmp/.v\n")
        sb.append("fi\n")
    }

    fun setRate(hz: Int): Pair<Boolean, String> {
        val sb = StringBuilder()
        sb.append("settings put system peak_refresh_rate $hz.0\n")
        sb.append("settings put system min_refresh_rate $hz.0\n")
        sb.append("settings put system user_refresh_rate $hz\n")
        sb.append("if [ -d ${HZ_ACTION.substringBeforeLast('/')} ]; then\n")
        sb.append("  echo $hz > $HZ_CONF 2>/dev/null\n")
        sb.append("  sh $HZ_ACTION >/dev/null 2>&1\n")
        sb.append("fi\n")
        sb.append("mkdir -p /data/adb\necho $hz > /data/adb/990oc_rate 2>/dev/null\n")
        return su(sb.toString())
    }

    fun readRate(): Int {
        val (_, out) = su("settings get system peak_refresh_rate 2>/dev/null", 8000)
        return out.trim().toFloatOrNull()?.toInt() ?: 0
    }
}
