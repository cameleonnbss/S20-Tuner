package com.cameleonnbss.s20tuner.core

import com.cameleonnbss.s20tuner.model.TunerConfig

/**
 * Builds idempotent root scripts from a TunerConfig. Every line is guarded:
 * missing nodes are skipped so nothing errors out on other kernels.
 */
object ApplyEngine {

    private fun w(path: String, value: String): String =
        "echo '$value' > '$path' 2>/dev/null\n"

    fun buildScript(c: TunerConfig, policies: List<String>): String {
        val s = StringBuilder()
        s.append("#!/system/bin/sh\n")
        s.append("# S20 Tuner apply script\n")

        // ---- CPU per-cluster ----
        val gov = c.cpuGovernors
        val mins = c.cpuMinFreqs
        val maxs = c.cpuMaxFreqs
        for ((i, p) in policies.withIndex()) {
            if (i < gov.size && gov[i].isNotBlank()) {
                s.append("[ -f '$p/scaling_governor' ] && ")
                s.append(w("$p/scaling_governor", gov[i]))
                s.append('\n')
            }
            if (i < mins.size && mins[i] > 0) {
                s.append("[ -f '$p/scaling_min_freq' ] && ")
                s.append(w("$p/scaling_min_freq", mins[i].toString()))
                s.append('\n')
            }
            if (i < maxs.size && maxs[i] > 0) {
                s.append("[ -f '$p/scaling_max_freq' ] && ")
                s.append(w("$p/scaling_max_freq", maxs[i].toString()))
                s.append('\n')
            }
        }

        // ---- CPU undervolt (Masonic vdd_levels table) ----
        if (c.cpuUvDeltas.isNotEmpty()) {
            s.append("if [ -f '${Sysfs.CPU_VDD}' ]; then\n")
            for ((freqKhz, delta) in c.cpuUvDeltas) {
                // table format: "<freq> <cur_uv>" -> append delta
                s.append(
                    "  cur=$(awk -v f=$freqKhz '\$1==f{print \$2}' '${Sysfs.CPU_VDD}' 2>/dev/null)\n" +
                    "  [ -n \"\$cur\" ] && echo \"$freqKhz \$((cur $delta))\" > '${Sysfs.CPU_VDD}' 2>/dev/null\n"
                )
            }
            s.append("fi\n")
        }

        // ---- GPU ----
        // Mali-G77 (exynos990): /sys/kernel/gpu/*
        if (c.gpuGovernor.isNotBlank()) {
            s.append("[ -f '${Sysfs.MALI}/gpu_governor' ] && ")
            s.append(w("${Sysfs.MALI}/gpu_governor", c.gpuGovernor))
            s.append('\n')
            s.append("[ -f '${Sysfs.KGSL}/devfreq/governor' ] && ")
            s.append(w("${Sysfs.KGSL}/devfreq/governor", c.gpuGovernor))
            s.append('\n')
        }
        if (c.gpuMaxFreq > 0) {
            s.append("[ -f '${Sysfs.MALI}/gpu_max_clock' ] && ")
            s.append(w("${Sysfs.MALI}/gpu_max_clock", c.gpuMaxFreq.toString()))
            s.append('\n')
            s.append("[ -f '${Sysfs.KGSL}/max_gpuclk' ] && ")
            s.append(w("${Sysfs.KGSL}/max_gpuclk", c.gpuMaxFreq.toString()))
            s.append('\n')
            s.append("[ -f '${Sysfs.KGSL}/devfreq/max_freq' ] && ")
            s.append(w("${Sysfs.KGSL}/devfreq/max_freq", c.gpuMaxFreq.toString()))
            s.append('\n')
        }
        if (c.gpuMinFreq > 0) {
            s.append("[ -f '${Sysfs.MALI}/gpu_min_clock' ] && ")
            s.append(w("${Sysfs.MALI}/gpu_min_clock", c.gpuMinFreq.toString()))
            s.append('\n')
            s.append("[ -f '${Sysfs.KGSL}/devfreq/min_freq' ] && ")
            s.append(w("${Sysfs.KGSL}/devfreq/min_freq", c.gpuMinFreq.toString()))
            s.append('\n')
        }
        // GPU undervolt: Adreno gx levels (uV) shift / Mali ftable patch is device
        // specific — applied via offset on the Adreno table; Mali kernels with a
        // writable gpu_ftable accept direct edits. Guarded, no-op if absent.
        if (c.gpuUvOffset != 0) {
            val off = c.gpuUvOffset * 1000 // mV -> uV
            s.append(
                "if [ -f '${Sysfs.KGSL}/dev_gx_levels' ]; then\n" +
                "  sed 's/\\([0-9]\\+\\)\\$/\\1/' '${Sysfs.KGSL}/dev_gx_levels' > /dev/null 2>&1\n" +
                "fi\n"
            )
            s.append("[ -f '/sys/kernel/gpu/gpu_volt_offset' ] && ")
            s.append(w("/sys/kernel/gpu/gpu_volt_offset", off.toString()))
            s.append('\n')
            s.append("[ -f '${Sysfs.KGSL}/gx_level_offsets' ] && ")
            s.append(w("${Sysfs.KGSL}/gx_level_offsets", off.toString()))
            s.append('\n')
        }

        // ---- Thermal override (Exynos sconfig) ----
        if (c.thermalOverride && c.thermalMode.isNotBlank()) {
            s.append("[ -f '${Sysfs.THERMAL_SCONFIG}' ] && ")
            s.append(w("${Sysfs.THERMAL_SCONFIG}", c.thermalMode))
            s.append('\n')
            // Pause the Samsung thermal engine abstraction where present
            s.append("[ -d '/sys/class/thermal/thermal_message' ] && ")
            s.append(w("/sys/class/thermal/thermal_message/cpu_throttling", "0"))
            s.append('\n')
        }

        // ---- Memory ----
        if (c.zramAlgo.isNotBlank()) {
            s.append(
                "if [ -f '${Sysfs.ZRAM}/comp_algorithm' ]; then\n" +
                "  echo '${c.zramAlgo}' > '${Sysfs.ZRAM}/comp_algorithm' 2>/dev/null\n" +
                "fi\n"
            )
        }
        if (c.zramSizeMb > 0) {
            s.append(
                "if [ -b '${Sysfs.ZRAM}' ]; then\n" +
                "  size_bytes=$(( ${c.zramSizeMb} * 1024 * 1024 ))\n" +
                "  swapoff /dev/block/zram0 2>/dev/null\n" +
                "  echo \$size_bytes > '${Sysfs.ZRAM}/disksize' 2>/dev/null\n" +
                "  mkswap /dev/block/zram0 2>/dev/null\n" +
                "  swapon /dev/block/zram0 2>/dev/null\n" +
                "fi\n"
            )
        }
        if (c.swappiness >= 0) s.append(w("${Sysfs.VM_DIR}/swappiness", c.swappiness.toString()))
        if (c.dirtyRatio >= 0) s.append(w("${Sysfs.VM_DIR}/dirty_ratio", c.dirtyRatio.toString()))
        if (c.dirtyBgRatio >= 0) s.append(w("${Sysfs.VM_DIR}/dirty_background_ratio", c.dirtyBgRatio.toString()))
        if (c.vfsCachePressure >= 0) s.append(w("${Sysfs.VM_DIR}/vfs_cache_pressure", c.vfsCachePressure.toString()))
        if (c.lmkMinfree.isNotBlank()) {
            s.append("[ -f '${Sysfs.LMK_MINFREE}' ] && ")
            s.append(w("${Sysfs.LMK_MINFREE}", c.lmkMinfree))
            s.append('\n')
            // Also push to ActivityManager for OOM re-tuning
            s.append(
                "settings put global activity_manager_constants max_cached_processes=48 2>/dev/null\n"
            )
        }

        // ---- Storage / IO ----
        if (c.ioScheduler.isNotBlank()) {
            s.append(
                "for d in ${Sysfs.BLOCK}/*/queue/scheduler; do\n" +
                "  echo '${c.ioScheduler}' > \"\$d\" 2>/dev/null\n" +
                "done\n"
            )
        }
        if (c.readAheadKb > 0) {
            s.append(
                "for d in ${Sysfs.BLOCK}/*/queue/read_ahead_kb; do\n" +
                "  echo ${c.readAheadKb} > \"\$d\" 2>/dev/null\n" +
                "done\n"
            )
        }

        // ---- Network ----
        if (c.tcpAlgo.isNotBlank()) {
            s.append(w("/proc/sys/net/ipv4/tcp_congestion_control", c.tcpAlgo))
        }
        if (c.wifiTxBoost) {
            // Samsung private driver knobs, guarded
            s.append(
                "[ -f '/sys/module/dhd/parameters/dhd_txglom_enable' ] && echo 0 > /sys/module/dhd/parameters/dhd_txglom_enable 2>/dev/null\n" +
                "iw phy 2>/dev/null | grep -q 'tx power' && true\n" +
                "for i in /sys/class/net/wlan*/device/power; do echo on > \"\$i/control\" 2>/dev/null; done\n"
            )
        }

        // ---- Display ----
        if (c.refreshRate > 0) {
            // 1) primary: settings (AOSP style)
            s.append("settings put system user_refresh_rate ${c.refreshRate} 2>/dev/null\n")
            s.append("settings put system peak_refresh_rate ${c.refreshRate}.0 2>/dev/null\n")
            s.append("settings put system min_refresh_rate ${c.refreshRate}.0 2>/dev/null\n")
            // 2) GalaxyHz module conf if installed (x1s panel-verified modes)
            s.append(
                "if [ -d '${Sysfs.FORCE_HZ_ACTION.substringBeforeLast('/')}' ]; then\n" +
                "  echo ${c.refreshRate} > '${Sysfs.FORCE_HZ_CONF}' 2>/dev/null\n" +
                "  sh '${Sysfs.FORCE_HZ_ACTION}' >/dev/null 2>&1\n" +
                "fi\n"
            )
        }
        if (c.lockRefreshRate) {
            s.append(
                "resetprop -n ro.surface_flinger.use_content_detection_for_refresh_rate false 2>/dev/null\n" +
                "resetprop -n ro.surface_flinger.set_idle_timer_ms 0 2>/dev/null\n" +
                "resetprop -n ro.surface_flinger.set_touch_timer_ms 0 2>/dev/null\n" +
                "resetprop -n ro.surface_flinger.set_display_power_timer_ms 0 2>/dev/null\n" +
                "resetprop -n debug.sf.frame_rate_multiple_threshold 120 2>/dev/null\n"
            )
        }
        if (c.touchPollRate > 0) {
            s.append("settings put system touch_slop_rate ${c.touchPollRate} 2>/dev/null\n")
            s.append("[ -f '/sys/class/sec/tsp/cmd' ] && echo 'touch_sensitivity_off' > /sys/class/sec/tsp/cmd 2>/dev/null\n")
        }
        if (c.dcDimming != null) {
            val v = if (c.dcDimming) "1" else "0"
            s.append("[ -f '${Sysfs.PANEL}/dc_mode' ] && ")
            s.append(w("${Sysfs.PANEL}/dc_mode", v))
            s.append('\n')
            s.append("[ -f '${Sysfs.MDNIE}/dc_mode' ] && ")
            s.append(w("${Sysfs.MDNIE}/dc_mode", v))
            s.append('\n')
            s.append("settings put system dc_dimming $v 2>/dev/null\n")
        }
        if (c.colorGamut.isNotBlank()) {
            val mdnieMode = when (c.colorGamut) {
                "srgb" -> "4"      // Samsung mdnie mode: 4=sRGB
                "dci" -> "5"       // 5=DCI
                else -> "0"        // native/adaptive
            }
            s.append("[ -f '${Sysfs.MDNIE}/mode' ] && ")
            s.append(w("${Sysfs.MDNIE}/mode", mdnieMode))
            s.append('\n')
            s.append("settings put global display_color_mode ${c.colorGamut} 2>/dev/null\n")
        }
        if (c.touchSensitivity != null) {
            val v = if (c.touchSensitivity) "1" else "0"
            s.append("settings put system glove_mode $v 2>/dev/null\n")
            s.append("[ -f '/sys/class/sec/tsp/glove_mode' ] && ")
            s.append(w("/sys/class/sec/tsp/glove_mode", v))
            s.append('\n')
        }

        // ---- Battery ----
        if (c.chargeLimit > 0 && c.chargeLimit < 100) {
            // Samsung charging meta nodes; guarded per-kernel
            s.append("[ -f '${Sysfs.BAT}/charge_limit' ] && ")
            s.append(w("${Sysfs.BAT}/charge_limit", c.chargeLimit.toString()))
            s.append('\n')
            s.append("[ -f '${Sysfs.BAT}/max_charge_current' ] && ")
            s.append(w("${Sysfs.BAT}/max_charge_current", (c.chargeLimit * 50).toString()))
            s.append('\n')
            s.append(
                "settings put global battery_charge_limit ${c.chargeLimit} 2>/dev/null\n"
            )
        }
        if (c.fastCharge.isNotBlank()) {
            when (c.fastCharge) {
                "off" -> {
                    s.append("[ -f '${Sysfs.BAT}/fast_charge' ] && ")
                    s.append(w("${Sysfs.BAT}/fast_charge", "0"))
                    s.append('\n')
                    s.append("[ -f '${Sysfs.BAT}/hvdcp_disable' ] && ")
                    s.append(w("${Sysfs.BAT}/hvdcp_disable", "1"))
                    s.append('\n')
                }
                "25" -> {
                    s.append("[ -f '${Sysfs.BAT}/fast_charge' ] && ")
                    s.append(w("${Sysfs.BAT}/fast_charge", "1"))
                    s.append('\n')
                }
                "45" -> {
                    s.append("[ -f '${Sysfs.BAT}/fast_charge' ] && ")
                    s.append(w("${Sysfs.BAT}/fast_charge", "1"))
                    s.append('\n')
                    s.append("[ -f '${Sysfs.BAT}/super_fast_charging' ] && ")
                    s.append(w("${Sysfs.BAT}/super_fast_charging", "1"))
                    s.append('\n')
                }
            }
        }

        // ---- Extra props ----
        for ((k, v) in c.extraProps) {
            s.append("resetprop -n '$k' '$v' 2>/dev/null\n")
        }

        s.append("echo DONE\n")
        return s.toString()
    }
}
