package com.cameleonnbss.s20tuner.core

data class DeviceInfo(
    val device: String = "",
    val soc: String = "",
    val kernel: String = "",
    val isExynos990: Boolean = false,
    val tables: Map<String, List<Int>> = emptyMap(),   // policy -> freq table (kHz)
    val currentGovs: List<String> = emptyList(),
    val availGovs: List<String> = emptyList(),
    val gpuTable: List<Int> = emptyList(),             // kHz
    val gpuGovs: List<String> = emptyList(),
    val hasVdd: Boolean = false
)

object DeviceProbe {

    fun probe(): DeviceInfo {
        val r = Shell.exec(Sysfs.probeScript(), 20000)
        val values = HashMap<String, String>()
        var cur = ""
        val acc = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) values[cur] = acc.toString().trim()
            acc.setLength(0)
        }
        for (line in r.out.lines()) {
            if (line.startsWith("P ")) { flush(); cur = line.substring(2).trim() }
            else { if (acc.isNotEmpty()) acc.append('\n'); acc.append(line) }
        }
        flush()

        val platform = (values["soc"] ?: "").lowercase()
        val device = values["soc"]?.lines()?.getOrNull(1)?.trim() ?: ""
        val is990 = platform.contains("exynos990") || platform.contains("universal9825") ||
                device.startsWith("x1") || device.startsWith("n1s") ||
                device.startsWith("b0s") || device.startsWith("t2s")

        val tables = HashMap<String, List<Int>>()
        val govs = ArrayList<String>()
        val availGovs = LinkedHashSet<String>()
        for (p in Sysfs.POLICIES) {
            values["${p}_av"]?.let { t ->
                tables[p] = t.split(" ").mapNotNull { it.toIntOrNull() }.sorted()
            }
            values["${p}_gov"]?.let { if (it.isNotBlank()) govs.add(it.trim()) }
            values["${p}_govs"]?.let { block ->
                block.split(" ").map { it.trim() }.filter { it.isNotEmpty() }.forEach { availGovs.add(it) }
            }
        }

        val gpuTable = (values["gpu_av"] ?: "")
            .split(" ", "\n").mapNotNull { it.toIntOrNull() }.sorted()
        val gpuGovs = (values["gpu_govs"] ?: "")
            .split(" ", "\n").map { it.trim() }.filter { it.isNotEmpty() }

        return DeviceInfo(
            device = device,
            soc = platform,
            kernel = values["kern"] ?: "",
            isExynos990 = is990,
            tables = tables,
            currentGovs = govs,
            availGovs = availGovs.toList().ifEmpty { listOf("schedutil", "performance", "powersave") },
            gpuTable = gpuTable,
            gpuGovs = gpuGovs.ifEmpty { listOf("simple_ondemand", "performance", "powersave") },
            hasVdd = (values["vdd"] ?: "").isNotBlank()
        )
    }
}
