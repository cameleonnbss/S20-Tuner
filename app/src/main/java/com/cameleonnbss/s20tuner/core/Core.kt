package com.cameleonnbss.s20tuner.core

import java.util.concurrent.TimeUnit

object Core {
    private const val CPUDIR = "/sys/devices/system/cpu/cpufreq"
    private const val VDD = "/sys/devices/system/cpu/cpu0/cpufreq/vdd_levels"
    private const val GPU = "/sys/kernel/gpu"
    private const val UV_FILE = "/data/adb/990oc_uv"
    private const val CFG_FILE = "/data/adb/990oc.cfg"
    private const val BOOT_FILE = "/data/adb/990oc_boot.sh"

    data class Probe(
        val pols: List<String> = emptyList(),           // sorted little -> prime
        val tables: Map<String, List<Int>> = emptyMap(),
        val gpuTable: List<Int> = emptyList(),
        val gpuGovs: List<String> = emptyList(),
        val ceilings: List<Int> = emptyList(),          // firmware hidden max per policy (ECT)
        val gpuCeil: Int = 0,                           // firmware hidden GPU max (ECT G3D)
        val gpuMax: Int = 0,
        val hasVdd: Boolean = false,
        val device: String = ""
    )

    data class Cfg(
        val minPct: List<Int> = listOf(-1, -1, -1),      // -1 = table minimum
        val maxPct: List<Int> = listOf(100, 100, 100),   // percent of table max
        val gov: String = "schedutil",
        val gpuMaxPct: Int = 100,
        val gpuGov: String = "",                          // empty = leave GPU governor alone
        val uv: Int = 0                                  // microvolts, negative = undervolt
    )

    val BEST = Cfg(
        minPct = listOf(80, 85, 90), maxPct = listOf(100, 100, 100),
        gov = "performance", gpuMaxPct = 100, gpuGov = "", uv = -10000
    )

    val PRESETS = linkedMapOf(
        "Stock" to Cfg(),
        "Underclock" to Cfg(listOf(-1, -1, -1), listOf(80, 85, 85), "schedutil", 70, "simple_ondemand", -20000),
        "Sleep" to Cfg(listOf(-1, -1, -1), listOf(60, 65, 70), "powersave", 50, "powersave", 0)
    )

    // devices shipped with Exynos 990 (codenames)
    val KNOWN_990 = listOf("x1s", "y2s", "z3s", "c1s", "c2s", "r8s")

    // ECT dvfs_table: domain -> max level (includes levels the kernel never registered)
    fun parseEct(text: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        var dom = ""
        for (raw in text.lines()) {
            val l = raw.trim()
            if (l.startsWith("[DOMAIN NAME]")) {
                dom = l.substringAfter(":").trim(); continue
            }
            if (l.startsWith("[LEVEL]")) {
                val f = l.substringAfter(":").trim().substringBefore("(").trim().toIntOrNull() ?: continue
                if (dom.isNotEmpty()) out[dom] = maxOf(out[dom] ?: 0, f)
            }
        }
        return out
    }

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

    private fun parseTagged(out: String): Map<String, String> {
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
        return values
    }

    fun probe(): Probe {
        val sb = StringBuilder()
        sb.append("echo P pols\nls -d $CPUDIR/policy* 2>/dev/null\n")
        sb.append("echo P dev\ngetprop ro.product.device\n")
        sb.append("echo P gmax\ncat $GPU/gpu_max_clock 2>/dev/null\n")
        sb.append("echo P gtab\ncat $GPU/gpu_freq_table 2>/dev/null\n")
        sb.append("echo P ggov\ncat $GPU/gpu_available_governor 2>/dev/null\n")
        sb.append("echo P vdd\nhead -c 100 $VDD 2>/dev/null\n")
        sb.append("mount -t debugfs none /sys/kernel/debug 2>/dev/null\n")
        sb.append("echo P ect\ncat /sys/kernel/debug/ect/dvfs_table 2>/dev/null\n")
        val (_, out) = su(sb.toString(), 25000)
        val v = parseTagged(out)

        // dynamic policies, sorted little -> prime by their max frequency
        val pols = (v["pols"] ?: "").lines()
            .map { it.trim() }
            .filter { it.contains("policy") }
            .distinct()
            .sortedBy { it.substringAfterLast("policy").toIntOrNull() ?: 99 }

        val tables = HashMap<String, List<Int>>()
        if (pols.isNotEmpty()) {
            val sb2 = StringBuilder()
            pols.forEach { p ->
                sb2.append("echo P t${pols.indexOf(p)}\ncat $p/scaling_available_frequencies 2>/dev/null\n")
            }
            val (_, out2) = su(sb2.toString(), 20000)
            val v2 = parseTagged(out2)
            pols.forEachIndexed { i, p ->
                v2["t$i"]?.let {
                    tables[p] = it.split(Regex("[\\s]+")).mapNotNull { n -> n.toIntOrNull() }.sorted()
                }
            }
        }
        // keep only policies that actually have a table, ordered by max (little->prime)
        val good = pols.filter { (tables[it]?.maxOrNull() ?: 0) > 0 }
            .sortedBy { tables[it]?.maxOrNull() ?: 0 }

        val gpuTable = (v["gtab"] ?: "").trim()
            .split(Regex("[\\s]+")).mapNotNull { it.toIntOrNull() }.sorted()

        // firmware hidden ceilings from ECT, aligned with policies (both ascending)
        val ect = parseEct(v["ect"] ?: "")
        val cpuCeils = ect.entries.filter { it.key.startsWith("CPUCL") }
            .sortedBy { it.value }.map { it.value }
        val ceilings = (0 until good.size).map { i -> cpuCeils.getOrElse(i) { 0 } }

        return Probe(
            pols = good,
            tables = tables,
            gpuTable = gpuTable,
            gpuGovs = (v["ggov"] ?: "").trim().split(Regex("[\\s]+")).filter { it.isNotBlank() },
            ceilings = ceilings,
            gpuCeil = ect["G3D"] ?: 0,
            gpuMax = v["gmax"]?.trim()?.toIntOrNull() ?: 0,
            hasVdd = (v["vdd"] ?: "").isNotBlank(),
            device = v["dev"]?.trim() ?: ""
        )
    }

    fun pollScript(pr: Probe): String = StringBuilder().apply {
        pr.pols.forEachIndexed { i, p ->
            append("echo L$i \$(cat $p/scaling_cur_freq 2>/dev/null)\n")
            append("echo MX$i \$(cat $p/scaling_max_freq 2>/dev/null)\n")
        }
        append("echo GPUF \$(cat $GPU/gpu_clock 2>/dev/null || cat $GPU/gpu_freq 2>/dev/null)\n")
        append("echo GMAX \$(cat $GPU/gpu_max_clock 2>/dev/null)\n")
        append("echo TEMP \$(cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null)\n")
        append("echo LOAD \$(cut -d' ' -f1 /proc/loadavg 2>/dev/null)\n")
    }.toString()

    // nearest allowed GPU table value <= target (never exceeds the kernel table)
    private fun gpuTarget(pr: Probe, pct: Int): Int? {
        if (pr.gpuTable.isEmpty()) return null
        val stock = pr.gpuTable.maxOrNull() ?: return null
        val want = stock * pct / 100
        return pr.gpuTable.filter { it <= want }.maxOrNull() ?: pr.gpuTable.first()
    }

    private fun clockLines(pr: Probe, c: Cfg): String = StringBuilder().apply {
        pr.pols.forEachIndexed { i, pol ->
            val t = pr.tables[pol].orEmpty()
            val mx = t.maxOrNull() ?: return@forEachIndexed
            val mn = t.minOrNull() ?: 0
            val minV = if (i < c.minPct.size && c.minPct[i] in 1..99) mx * c.minPct[i] / 100 else mn
            val maxV = if (i < c.maxPct.size) mx * c.maxPct[i] / 100 else mx
            append("[ -f $pol/scaling_governor ] && echo '${c.gov}' > $pol/scaling_governor 2>/dev/null\n")
            append("[ -f $pol/scaling_max_freq ] && echo $maxV > $pol/scaling_max_freq 2>/dev/null\n")
            append("[ -f $pol/scaling_min_freq ] && echo $minV > $pol/scaling_min_freq 2>/dev/null\n")
        }
        gpuTarget(pr, c.gpuMaxPct)?.let { g ->
            append("[ -f $GPU/gpu_max_clock ] && echo $g > $GPU/gpu_max_clock 2>/dev/null\n")
        }
        if (c.gpuGov.isNotBlank()) {
            append("[ -f $GPU/gpu_governor ] && echo '${c.gpuGov}' > $GPU/gpu_governor 2>/dev/null\n")
        }
    }.toString()

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
        val clocks = clockLines(pr, c)
        val live = StringBuilder("mkdir -p /data/adb\n")
        live.append(clocks)
        uvShift(c.uv, live)
        // boot script: same values; kernel vdd table is stock right after boot
        val boot = StringBuilder("#!/system/bin/sh\n# generated by 990 OC\necho 0 > $UV_FILE 2>/dev/null\n")
        boot.append(clocks)
        uvShift(c.uv, boot)
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
