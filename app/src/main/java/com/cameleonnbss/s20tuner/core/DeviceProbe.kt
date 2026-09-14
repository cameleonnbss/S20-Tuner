package com.cameleonnbss.s20tuner.core

data class DeviceInfo(
    val device: String = "",
    val model: String = "",
    val android: String = "",
    val kernel: String = "",
    val buildId: String = "",
    val soc: String = "",            // "exynos990" or "sm8250"
    val codename: String = "",       // x1s / y2s / ...
    val cpuPolicies: List<String> = emptyList(),
    val policyFreqs: Map<String, List<Int>> = emptyMap(),   // available frequencies
    val policyGovs: Map<String, String> = emptyMap(),
    val gpuFreqs: List<Int> = emptyList(),
    val gpuGovs: List<String> = emptyList(),
    val tcpAvailable: List<String> = emptyList(),
    val ioSchedulers: List<String> = emptyList(),
    val zramAlgos: List<String> = emptyList(),
    val rootOk: Boolean = false,
    val isSupported: Boolean = false
)

object DeviceProbe {

    /**
     * Full probe in ONE su session so it's fast. Output: P <key> then V lines.
     */
    fun probe(): DeviceInfo {
        val sb = StringBuilder()
        val props = listOf(
            "ro.product.device", "ro.product.model", "ro.build.version.release",
            "ro.build.display.id", "ro.kernel.version", "ro.board.platform",
            "ro.product.brand"
        )
        for (p in props) {
            sb.append("echo P $p\ngetprop $p\n")
        }
        sb.append("echo P kernel\nuname -r\n")
        // discover policies
        sb.append("echo P policies\nls ${Sysfs.CPU_BASE} | grep policy\n")
        // per-policy freq tables
        for (pol in Sysfs.cpuPolicyPaths()) {
            sb.append("echo P ${pol}\n")
            sb.append("cat $pol/scaling_available_frequencies 2>/dev/null\n")
            sb.append("cat $pol/scaling_governor 2>/dev/null\n")
            sb.append("cat $pol/scaling_available_governors 2>/dev/null\n")
        }
        // GPU (both backends)
        sb.append("echo P mali_govs\n")
        sb.append("cat ${Sysfs.MALI}/gpu_governor 2>/dev/null\n")
        sb.append("ls /sys/kernel/gpu/ 2>/dev/null\n")
        sb.append("cat ${Sysfs.MALI}/gpu_max_clock 2>/dev/null\n")
        sb.append("echo P adreno_govs\n")
        sb.append("cat ${Sysfs.KGSL}/devfreq/governor 2>/dev/null\n")
        sb.append("cat ${Sysfs.KGSL}/devfreq/available_governors 2>/dev/null\n")
        sb.append("cat ${Sysfs.KGSL}/devfreq/available_frequencies 2>/dev/null\n")
        // IO schedulers of the data partition
        sb.append("echo P iosched\n")
        sb.append("cat ${Sysfs.BLOCK}/sda/queue/scheduler 2>/dev/null || cat ${Sysfs.BLOCK}/mmcblk0/queue/scheduler 2>/dev/null\n")
        // TCP
        sb.append("echo P tcp\ncat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null\n")
        // ZRAM
        sb.append("echo P zram\ncat ${Sysfs.ZRAM}/comp_algorithm 2>/dev/null\n")
        sb.append("echo P battery\ncat ${Sysfs.BAT}/uevent 2>/dev/null | head -20\n")
        sb.append("echo P mdnie\nls ${Sysfs.MDNIE} 2>/dev/null | head -20\n")

        val res = Shell.exec(sb.toString(), 25000)
        val values = HashMap<String, String>()
        var cur = ""
        val acc = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) values[cur] = acc.toString().trim()
            acc.setLength(0)
        }
        for (line in res.out.lines()) {
            if (line.startsWith("P ")) { flush(); cur = line.substring(2).trim() }
            else { if (acc.isNotEmpty()) acc.append('\n'); acc.append(line) }
        }
        flush()

        val device = values["ro.product.device"] ?: ""
        val platform = (values["ro.board.platform"] ?: "").lowercase()
        val codename = when {
            device.startsWith("x1") -> "x1s"
            device.startsWith("y2") -> "y2s"
            else -> device
        }
        val soc = when {
            platform.contains("exynos990") || codename == "x1s" -> "exynos990"
            platform.contains("kona") || platform.contains("sm8250") || codename == "y2s" -> "sm8250"
            else -> platform
        }
        val isSupported = listOf("x1s", "y2s", "x1q", "y2q").any { device.startsWith(it) } ||
                soc.contains("990") || soc.contains("8250")

        val policies = values["policies"]?.lines()
            ?.filter { it.startsWith("policy") }
            ?.sortedBy { it.removePrefix("policy").toIntOrNull() ?: 0 }
            ?.map { "${Sysfs.CPU_BASE}/$it" } ?: emptyList()

        val policyFreqs = HashMap<String, List<Int>>()
        val policyGovs = HashMap<String, String>()
        for (p in policies) {
            values[p]?.let { block ->
                val lines = block.lines().filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    policyFreqs[p] = lines[0].trim().split(" ").mapNotNull { it.toIntOrNull() }
                }
                if (lines.size >= 2) policyGovs[p] = lines[1].trim()
            }
        }

        val gpuFreqs = values["adreno_govs"]?.lines()?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()
        val maliMax = values["mali_govs"]?.lines()
            ?.firstOrNull { it.contains("\\d{6}".toRegex()) }?.trim()?.toIntOrNull() ?: -1

        val ioSched = (values["iosched"] ?: "")
            .replace("[", "").replace("]", "")
            .split(" ").map { it.trim() }.filter { it.isNotEmpty() }

        val tcp = (values["tcp"] ?: "").split(" ").map { it.trim() }.filter { it.isNotEmpty() }

        val zramAlgos = (values["zram"] ?: "")
            .replace("[", "").replace("]", "")
            .split(" ").map { it.trim() }.filter { it.isNotEmpty() }

        val gpuGovsList = ArrayList<String>()
        values["adreno_govs"]?.let { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.size >= 2) gpuGovsList.addAll(lines[1].split(" ").map { it.trim() })
        }
        gpuGovsList.addAll(listOf("simple_ondemand", "performance", "powersave"))

        return DeviceInfo(
            device = device,
            model = values["ro.product.model"] ?: "",
            android = values["ro.build.version.release"] ?: "",
            kernel = values["kernel"] ?: "",
            buildId = values["ro.build.display.id"] ?: "",
            soc = soc,
            codename = codename,
            cpuPolicies = policies,
            policyFreqs = policyFreqs,
            policyGovs = policyGovs,
            gpuFreqs = (gpuFreqs + maliMax).filter { it > 0 }.distinct().sorted(),
            gpuGovs = gpuGovsList.distinct(),
            tcpAvailable = tcp,
            ioSchedulers = ioSched,
            zramAlgos = zramAlgos,
            rootOk = res.ok,
            isSupported = isSupported
        )
    }
}
