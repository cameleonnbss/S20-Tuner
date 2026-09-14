package com.cameleonnbss.s20tuner.core

/**
 * Sysfs / proc paths for the Galaxy S20 series (exynos990 "x1s" and
 * snapdragon865 "y2s"), with dynamic fallbacks. All paths are best-effort:
 * missing nodes are simply skipped by the applicator.
 */
object Sysfs {

    // ---------- CPU ----------
    const val CPU_BASE = "/sys/devices/system/cpu/cpufreq"

    fun cpuPolicyPaths(): List<String> = listOf(
        "$CPU_BASE/policy0", "$CPU_BASE/policy4", "$CPU_BASE/policy6", "$CPU_BASE/policy7"
    )

    // Masonic-style per-core voltage table
    const val CPU_VDD = "/sys/devices/system/cpu/cpu0/cpufreq/vdd_levels"

    // ---------- GPU (detected at runtime) ----------
    const val KGSL = "/sys/class/kgsl/kgsl-3d0"        // Adreno (y2s)
    const val MALI = "/sys/kernel/gpu"                  // Mali-G77 (x1s)

    // ---------- Thermal ----------
    const val THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig"

    // ---------- Memory / ZRAM ----------
    const val ZRAM = "/sys/block/zram0"
    const val VM_DIR = "/proc/sys/vm"
    const val LMK_MINFREE = "/sys/module/lowmemorykiller/parameters/minfree"

    // ---------- IO ----------
    const val BLOCK = "/sys/block"

    // ---------- Battery ----------
    const val BAT = "/sys/class/power_supply/battery"

    // ---------- Display ----------
    const val MDNIE = "/sys/class/mdnie/mdnie"
    const val PANEL = "/sys/class/backlight/panel"
    // Existing GalaxyHz module integration
    const val FORCE_HZ_CONF = "/data/adb/force_hz.conf"
    const val FORCE_HZ_ACTION = "/data/adb/modules/force_120hz_x1s/action.sh"

    // Tuner data dir (boot module reads from here)
    const val TUNER_DIR = "/data/adb/s20tuner"

    fun pathsForProbe(): List<String> = listOf(
        "$CPU_BASE/policy0/scaling_cur_freq", "$CPU_BASE/policy4/scaling_cur_freq",
        "$CPU_BASE/policy6/scaling_cur_freq", "$CPU_BASE/policy7/scaling_cur_freq",
        "$CPU_BASE/policy0/scaling_available_frequencies",
        "$CPU_BASE/policy4/scaling_available_frequencies",
        "$CPU_BASE/policy6/scaling_available_frequencies",
        "$CPU_BASE/policy0/scaling_available_governors",
        "$CPU_BASE/policy0/scaling_governor", "$CPU_BASE/policy4/scaling_governor",
        "$CPU_BASE/policy6/scaling_governor",
        "$MALI/gpu_max_clock", "$MALI/gpu_min_clock", "$MALI/gpu_busy",
        "$MALI/gpu_freq", "$MALI/gpu_governor",
        "$KGSL/max_gpuclk", "$KGSL/gpuclk", "$KGSL/gpu_busy_percentage",
        "$KGSL/dev_gx_levels", "$KGSL/gpu_available_frequencies",
        "$THERMAL_SCONFIG",
        "$ZRAM/comp_algorithm", "$ZRAM/mm_stat", "$ZRAM/disksize",
        "$VM_DIR/swappiness", "$VM_DIR/dirty_ratio", "$VM_DIR/dirty_background_ratio",
        "$VM_DIR/vfs_cache_pressure",
        LMK_MINFREE,
        "$BAT/capacity", "$BAT/temp", "$BAT/status", "$BAT/current_now",
        "$BAT/voltage_now", "$BAT/charging_enabled", "$BAT/charge_limit",
        "$MDNIE/mode", "$PANEL/dc_mode", "$PANEL/hbm_mode",
        "/proc/sys/net/ipv4/tcp_available_congestion_control",
        "/proc/sys/net/ipv4/tcp_congestion_control",
        "ro.product.device", "ro.product.model", "ro.build.version.release",
        "ro.kernel.version", "ro.build.display.id"
    )

    fun probeScript(): String = buildString {
        for (p in pathsForProbe()) {
            val f = if (p.startsWith("ro.")) p else p
            if (p.startsWith("ro.")) {
                append("echo P ").append(p).append('\n')
                append("getprop '").append(p).append("' 2>/dev/null\n")
            } else {
                append("echo P ").append(p).append('\n')
                append("cat '").append(p).append("' 2>/dev/null | head -c 1024\n")
            }
        }
    }

    /** One-shot live status script: emits KEY value lines, parsed by TunerViewModel. */
    fun pollScript(): String = """
        echo CPU0 $(cat $CPU_BASE/policy0/scaling_cur_freq 2>/dev/null | tr ' ' ',')
        echo CPU4 $(cat $CPU_BASE/policy4/scaling_cur_freq 2>/dev/null | tr ' ' ',')
        echo CPU6 $(cat $CPU_BASE/policy6/scaling_cur_freq 2>/dev/null | tr ' ' ',')
        echo CPU7 $(cat $CPU_BASE/policy7/scaling_cur_freq 2>/dev/null | tr ' ' ',')
        echo MAX0 $(cat $CPU_BASE/policy0/scaling_max_freq 2>/dev/null)
        echo MAX4 $(cat $CPU_BASE/policy4/scaling_max_freq 2>/dev/null)
        echo MAX6 $(cat $CPU_BASE/policy6/scaling_max_freq 2>/dev/null)
        echo GPUF $(cat $MALI/gpu_freq 2>/dev/null)
        echo GPUB $(cat $MALI/gpu_busy 2>/dev/null)
        echo GPUKG $(cat $KGSL/gpuclk 2>/dev/null)
        echo GPUL $(cat $KGSL/gpu_busy_percentage 2>/dev/null)
        echo GMAX $(cat $MALI/gpu_max_clock 2>/dev/null)
        echo THERM $(cat $THERMAL_SCONFIG 2>/dev/null)
        echo TEMPBAT $(cat $BAT/temp 2>/dev/null)
        echo TEMPCPU $(cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null)
        echo TEMPGPU $(cat /sys/class/thermal/thermal_zone2/temp 2>/dev/null)
        echo BATP $(cat $BAT/capacity 2>/dev/null)
        echo BATCHG $(cat $BAT/status 2>/dev/null)
        echo BATIC $(cat $BAT/current_now 2>/dev/null)
        echo BATV $(cat $BAT/voltage_now 2>/dev/null)
        echo MEMT $(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print ${'$'}2}')
        echo MEMA $(grep MemAvailable /proc/meminfo 2>/dev/null | awk '{print ${'$'}2}')
        echo SWAPF $(grep SwapFree /proc/meminfo 2>/dev/null | awk '{print ${'$'}2}')
        echo SWAPT $(grep SwapTotal /proc/meminfo 2>/dev/null | awk '{print ${'$'}2}')
        echo ZRAMU $(cat $ZRAM/mem_used_total 2>/dev/null)
        echo FPS $(dumpsys display 2>/dev/null | grep -m1 -E 'renderFrameRate|mRefreshRate=' | head -c 60)
        echo RATE $(settings get system user_refresh_rate 2>/dev/null)
        echo UP $(cat /proc/uptime 2>/dev/null)
    """.trimIndent()
}
