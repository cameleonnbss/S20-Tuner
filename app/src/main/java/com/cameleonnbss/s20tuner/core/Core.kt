package com.cameleonnbss.s20tuner.core

import java.util.concurrent.TimeUnit

object Core {
    private const val CPU = "/sys/devices/system/cpu/cpufreq"
    val POL = listOf("$CPU/policy0", "$CPU/policy4", "$CPU/policy7") // A55 / A76 / M5
    private const val VDD = "/sys/devices/system/cpu/cpu0/cpufreq/vdd_levels"
    private const val GPU = "/sys/kernel/gpu"
    private const val UV_FILE = "/data/adb/990oc_uv"
    private const val CFG_FILE = "/data/adb/990oc.cfg"
    private const val BOOT_FILE = "/data/adb/990oc_boot.sh"

    data class Probe(
        val tables: Map<String, List<Int>> = emptyMap(),
        val gpuMax: Int = 0,
        val hasVdd: Boolean = false,
        val device: String = ""
    )

    data class Cfg(
        val minPct: List<Int> = listOf(-1, -1, -1),      // -1 = table minimum
        val maxPct: List<Int> = listOf(100, 100, 100),   // percent of table max
        val gov: String = "schedutil",
        val gpuMaxPct: Int = 100,
        val gpuGov: String = "simple_ondemand",
        val uv: Int = 0                                  // microvolts, negative = undervolt
    )

    val BEST = Cfg(
        minPct = listOf(80, 85, 90), maxPct = listOf(100, 100, 100),
        gov = "performance", gpuMaxPct = 100, gpuGov = "performance", uv = -10000
    )

    val PRESETS = linkedMapOf(
        "Stock" to Cfg(),
        "Underclock" to Cfg(listOf(-1, -1, -1), listOf(80, 85, 85), "schedutil", 70, "simple_ondemand", -20000),
        "Sleep" to Cfg(listOf(-1, -1, -1), listOf(60, 65, 70), "powersave", 50, "powersave", 0)
    )

    val GOVS = listOf("schedutil", "performance", "powersave", "conservative", "ondemand")
    val GPU_GOVS = listOf("simple_ondemand", "performance", "powersave")

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
        echo MX0 $(cat ${POL[0]}/scaling_max_freq 2>/dev/null)
        echo L4 $(cat ${POL[1]}/scaling_cur_freq 2>/dev/null)
        echo MX4 $(cat ${POL[1]}/scaling_max_freq 2>/dev/null)
        echo L7 $(cat ${POL[2]}/scaling_cur_freq 2>/dev/null)
        echo MX7 $(cat ${POL[2]}/scaling_max_freq 2>/dev/null)
        echo GPUF $(cat $GPU/gpu_freq 2>/dev/null)
        echo GMAX $(cat $GPU/gpu_max_clock 2>/dev/null)
        echo TEMP $(cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null)
        echo LOAD $(cut -d' ' -f1 /proc/loadavg 2>/dev/null)
    """.trimIndent()

    // guarded clock writes for one cluster
    private fun clocks(sb: StringBuilder, pol: String, gov: String, minV: Int, maxV: Int) {
        sb.append("[ -f $pol/scaling_governor ] && echo '$gov' > $pol/scaling_governor 2>/dev/null\n")
        sb.append("[ -f $pol/scaling_max_freq ] && echo $maxV > $pol/scaling_max_freq 2>/dev/null\n")
        sb.append("[ -f $pol/scaling_min_freq ] && echo $minV > $pol/scaling_min_freq 2>/dev/null\n")
    }

    private fun absMinMax(c: Cfg, pr: Probe, i: Int): Pair<Int, Int>? {
        val t = pr.tables[POL[i]].orEmpty()
        val mx = t.maxOrNull() ?: return null
        val mn = t.minOrNull() ?: 0
        val minV = if (c.minPct[i] in 1..99) mx * c.minPct[i] / 100 else mn
        return Pair(minV, mx * c.maxPct[i] / 100)
    }

    // diff-based undervolt so repeated applies never compound
    private fun uvShift(target: Int, sb: StringBuilder) {
        sb.append("CUR=\$(cat $UV_FILE 2>/dev/null); [ -z \"\$CUR\" ] && CUR=0\n")
        sb.append("SHIFT=\$(( $target - \$CUR ))\n")
        sb.append("if [ \"\$SHIFT\" -ne 0 ] && [ -f $VDD ]; then\n")
        sb.append("  awk -v d=\$SHIFT 'NF>=2{print \$1, \$2+d; next}{print}' $VDD > /data/local/tmp/.v 2>/dev/null\n")
        sb.append("  [ -s /data/local/tmp/.v ] && cat /data/local/tmp/.v > $VDD 2>/dev/null && echo $target > $UV_FILE 2>/dev/null\n")
        sb.append("  rm -f /data/local/tmp/.v\n")
        sb.append("fi\n")
    }

    fun applyCfg(c: Cfg, pr: Probe): Pair<Boolean, String> {
        val live = StringBuilder("mkdir -p /data/adb\n")
        POL.forEachIndexed { i, pol ->
            val (mn, mx) = absMinMax(c, pr, i) ?: return@forEachIndexed
            clocks(live, pol, c.gov, mn, mx)
        }
        uvShift(c.uv, live)
        if (pr.gpuMax > 0) {
            live.append("[ -f $GPU/gpu_max_clock ] && echo ${pr.gpuMax * c.gpuMaxPct / 100} > $GPU/gpu_max_clock 2>/dev/null\n")
        }
        live.append("[ -f $GPU/gpu_governor ] && echo '${c.gpuGov}' > $GPU/gpu_governor 2>/dev/null\n")

        // boot script: same values, but kernel vdd table is stock right after boot
        val boot = StringBuilder("#!/system/bin/sh\n# generated by 990 OC\necho 0 > $UV_FILE 2>/dev/null\n")
        POL.forEachIndexed { i, pol ->
            val (mn, mx) = absMinMax(c, pr, i) ?: return@forEachIndexed
            clocks(boot, pol, c.gov, mn, mx)
        }
        uvShift(c.uv, boot)
        if (pr.gpuMax > 0) {
            boot.append("[ -f $GPU/gpu_max_clock ] && echo ${pr.gpuMax * c.gpuMaxPct / 100} > $GPU/gpu_max_clock 2>/dev/null\n")
        }
        boot.append("[ -f $GPU/gpu_governor ] && echo '${c.gpuGov}' > $GPU/gpu_governor 2>/dev/null\n")

        live.append("cat > $BOOT_FILE <<'EOFB'\n${boot}EOFB\nchmod 755 $BOOT_FILE\n")
        live.append("echo '${ser(c)}' > $CFG_FILE 2>/dev/null\n")
        return su(live.toString())
    }

    fun ser(c: Cfg): String =
        "${c.minPct[0]}|${c.minPct[1]}|${c.minPct[2]}|${c.maxPct[0]}|${c.maxPct[1]}|${c.maxPct[2]}|${c.gov}|${c.gpuMaxPct}|${c.gpuGov}|${c.uv}"

    fun parse(s: String): Cfg? = try {
        val p = s.trim().split('|')
        Cfg(
            listOf(p[0].toInt(), p[1].toInt(), p[2].toInt()),
            listOf(p[3].toInt(), p[4].toInt(), p[5].toInt()),
            p[6], p[7].toInt(), p[8], p[9].toInt()
        )
    } catch (e: Exception) { null }

    fun currentCfg(): Cfg? {
        val (ok, out) = su("cat $CFG_FILE 2>/dev/null", 8000)
        return if (ok) parse(out) else null
    }
}
