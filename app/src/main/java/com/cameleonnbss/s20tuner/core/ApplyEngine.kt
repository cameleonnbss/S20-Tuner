package com.cameleonnbss.s20tuner.core

/**
 * Turns an OcConfig into a guarded root script for the Exynos 990.
 * Sentinels are resolved against the kernel's frequency tables.
 * Undervolt application is DIFF-BASED (stored in /data/adb/990oc/uv_applied)
 * so applying the same or different presets repeatedly never compounds.
 */
object ApplyEngine {

    fun resolveCpuMin(v: Int, table: List<Int>): Int? = when {
        v > 0 -> v
        v == -1 -> table.minOrNull()
        v == -2 -> table.maxOrNull()?.let { it * 60 / 100 }   // OC floor
        else -> null
    }

    fun resolveCpuMax(v: Int, table: List<Int>): Int? = when {
        v > 0 -> v
        v == -1 -> table.maxOrNull()
        v == -3 -> table.maxOrNull()?.let { it * 82 / 100 }   // UC cap
        v == -4 -> table.maxOrNull()?.let { it * 65 / 100 }   // deep UC cap
        else -> null
    }

    fun resolveGpuMin(v: Int, table: List<Int>): Int? = when {
        v > 0 -> v * 1000                       // UI works in MHz
        v == -1 -> table.minOrNull()
        v == -2 -> table.maxOrNull()?.let { it * 70 / 100 }
        else -> null
    }

    fun resolveGpuMax(v: Int, table: List<Int>): Int? = when {
        v > 0 -> v * 1000
        v == -1 -> table.maxOrNull()
        v == -3 -> table.maxOrNull()?.let { it * 70 / 100 }
        v == -4 -> table.maxOrNull()?.let { it * 50 / 100 }
        else -> null
    }

    fun buildScript(c: OcConfig, tables: Map<String, List<Int>>, gpuTable: List<Int>): String {
        val s = StringBuilder()
        s.append("#!/system/bin/sh\n# Exynos 990 OC apply - guarded, idempotent\n")
        s.append("mkdir -p ${Sysfs.DIR_PATH}\n")

        // ---- CPU clusters: A55 (policy0) / A76 (policy4) / M5 (policy7) ----
        Sysfs.POLICIES.forEachIndexed { i, pol ->
            val table = tables[pol] ?: emptyList()
            val gov = c.governors.getOrNull(i).orEmpty()
            if (gov.isNotBlank()) {
                s.append("[ -f $pol/scaling_governor ] && echo '$gov' > $pol/scaling_governor 2>/dev/null\n")
            }
            val mn = resolveCpuMin(c.clusterMinKhz.getOrNull(i) ?: -1, table)
            val mx = resolveCpuMax(c.clusterMaxKhz.getOrNull(i) ?: -1, table)
            if (mn != null) s.append("[ -f $pol/scaling_min_freq ] && echo $mn > $pol/scaling_min_freq 2>/dev/null\n")
            if (mx != null) s.append("[ -f $pol/scaling_max_freq ] && echo $mx > $pol/scaling_max_freq 2>/dev/null\n")
        }

        // ---- CPU undervolt: diff-based vdd table shift ----
        s.append(
            "AP=0; [ -f ${Sysfs.DIR_PATH}/uv_applied ] && AP=\$(cat ${Sysfs.DIR_PATH}/uv_applied 2>/dev/null); [ -z \"\$AP\" ] && AP=0\n" +
            "SHIFT=$(( ${c.cpuUvDelta} - AP ))\n" +
            "if [ \"\$SHIFT\" -ne 0 ] && [ -f ${Sysfs.VDD} ]; then\n" +
            "  awk -v d=\$SHIFT 'NF>=2{print \$1, \$2+d; next} {print}' ${Sysfs.VDD} > /data/local/tmp/.vdd_new 2>/dev/null\n" +
            "  if [ -s /data/local/tmp/.vdd_new ]; then cat /data/local/tmp/.vdd_new > ${Sysfs.VDD} 2>/dev/null; echo ${c.cpuUvDelta} > ${Sysfs.DIR_PATH}/uv_applied; fi\n" +
            "  rm -f /data/local/tmp/.vdd_new\n" +
            "fi\n"
        )

        // ---- GPU: Mali-G77 MP11 ----
        if (c.gpuGovernor.isNotBlank()) {
            s.append("[ -f ${Sysfs.GPU_GOV} ] && echo '${c.gpuGovernor}' > ${Sysfs.GPU_GOV} 2>/dev/null\n")
        }
        val gmn = resolveGpuMin(c.gpuMinMhz, gpuTable)
        val gmx = resolveGpuMax(c.gpuMaxMhz, gpuTable)
        if (gmn != null) s.append("[ -f ${Sysfs.GPU_MIN} ] && echo $gmn > ${Sysfs.GPU_MIN} 2>/dev/null\n")
        if (gmx != null) s.append("[ -f ${Sysfs.GPU_MAX} ] && echo $gmx > ${Sysfs.GPU_MAX} 2>/dev/null\n")

        // GPU voltage offset: absolute value, guarded
        if (c.gpuUvDelta != 0) {
            s.append("[ -f ${Sysfs.GPU_VOLT_OFFSET} ] && echo ${c.gpuUvDelta} > ${Sysfs.GPU_VOLT_OFFSET} 2>/dev/null\n")
        }

        // ---- thermal ----
        if (c.thermalMode.isNotBlank()) {
            s.append("[ -f ${Sysfs.SCONFIG} ] && echo '${c.thermalMode}' > ${Sysfs.SCONFIG} 2>/dev/null\n")
        }

        s.append("echo APPLIED\n")
        return s.toString()
    }
}
