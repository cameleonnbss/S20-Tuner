package com.cameleonnbss.s20tuner.core

/**
 * Exynos 990 (Galaxy S20 / S20+ / S20 Ultra / Note20, codenames x1s…) kernel
 * node paths. Everything is guarded at write time — missing nodes are skipped.
 */
object Sysfs {
    const val CPU = "/sys/devices/system/cpu/cpufreq"
    val POLICIES = listOf("$CPU/policy0", "$CPU/policy4", "$CPU/policy7") // A55 / A76 / M5
    const val VDD = "/sys/devices/system/cpu/cpu0/cpufreq/vdd_levels"     // Masonic-style µV table

    const val GPU = "/sys/kernel/gpu"            // Mali-G77 MP11
    const val GPU_MIN = "$GPU/gpu_min_clock"
    const val GPU_MAX = "$GPU/gpu_max_clock"
    const val GPU_CUR = "$GPU/gpu_freq"
    const val GPU_BUSY = "$GPU/gpu_busy"
    const val GPU_GOV = "$GPU/gpu_governor"
    const val GPU_GOV_AVAIL = "$GPU/gpu_governors"
    const val GPU_VOLT_OFFSET = "$GPU/gpu_volt_offset"   // µV, kernel-dependent

    const val SCONFIG = "/sys/class/thermal/thermal_message/sconfig"
    const val BAT_TEMP = "/sys/class/power_supply/battery/temp"
    const val BAT_PCT = "/sys/class/power_supply/battery/capacity"

    private const val STATE = "/data/local/tmp/990oc_state"
    private const val DIR = "/data/adb/990oc"

    fun probeScript(): String = buildString {
        for (p in POLICIES) {
            append("echo P ${p}_av\ncat $p/scaling_available_frequencies 2>/dev/null\n")
            append("echo P ${p}_gov\ncat $p/scaling_governor 2>/dev/null\n")
            append("echo P ${p}_govs\ncat $p/scaling_available_governors 2>/dev/null\n")
            append("echo P ${p}_cur\ncat $p/scaling_cur_freq 2>/dev/null\n")
        }
        append("echo P gpu_max\ncat $GPU_MAX 2>/dev/null\n")
        append("echo P gpu_av\ncat $GPU/available_frequencies 2>/dev/null || cat $GPU/gpu_available_frequencies 2>/dev/null\n")
        append("echo P gpu_govs\ncat $GPU_GOV_AVAIL 2>/dev/null\n")
        append("echo P vdd\nhead -c 400 $VDD 2>/dev/null\n")
        append("echo P soc\ngetprop ro.board.platform\ngetprop ro.product.device\n")
        append("echo P kern\nuname -r\n")
    }

    fun pollScript(): String = """
        echo L0 $(cat ${POLICIES[0]}/scaling_cur_freq 2>/dev/null | tr ' ' ',')
        echo L4 $(cat ${POLICIES[1]}/scaling_cur_freq 2>/dev/null | tr ' ' ',')
        echo L7 $(cat ${POLICIES[2]}/scaling_cur_freq 2>/dev/null | tr ' ' ',')
        echo MAX0 $(cat ${POLICIES[0]}/scaling_max_freq 2>/dev/null)
        echo MAX4 $(cat ${POLICIES[1]}/scaling_max_freq 2>/dev/null)
        echo MAX7 $(cat ${POLICIES[2]}/scaling_max_freq 2>/dev/null)
        echo GPUF $(cat $GPU_CUR 2>/dev/null)
        echo GPUB $(cat $GPU_BUSY 2>/dev/null)
        echo GMAXC $(cat $GPU_MAX 2>/dev/null)
        echo T7 $(cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null)
        echo TBAT $(cat $BAT_TEMP 2>/dev/null)
        echo BPCT $(cat $BAT_PCT 2>/dev/null)
        echo LOAD $(cut -d' ' -f1 /proc/loadavg 2>/dev/null)
        echo OCST $(cat $STATE 2>/dev/null)
    """.trimIndent()

    const val STATE_PATH = STATE
    const val DIR_PATH = DIR
}
