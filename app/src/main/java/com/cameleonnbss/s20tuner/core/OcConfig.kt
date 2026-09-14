package com.cameleonnbss.s20tuner.core

import org.json.JSONObject

/**
 * Exynos 990 overclock / underclock configuration.
 * -1 = "resolve from the kernel's frequency table" (max for OC, min for UC).
 * cpuUvDelta / gpuUvDelta are microvolts ADDED to the stock voltage table
 * (negative = undervolt).
 */
data class OcConfig(
    val clusterMinKhz: List<Int> = listOf(-1, -1, -1),   // policy0 / policy4 / policy7
    val clusterMaxKhz: List<Int> = listOf(-1, -1, -1),
    val governors: List<String> = listOf("schedutil", "schedutil", "schedutil"),
    val cpuUvDelta: Int = 0,
    val gpuMinMhz: Int = -1,
    val gpuMaxMhz: Int = -1,
    val gpuGovernor: String = "simple_ondemand",
    val gpuUvDelta: Int = 0,
    val thermalMode: String = ""                          // sconfig value, "" = untouched
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("min", org.json.JSONArray(clusterMinKhz))
        o.put("max", org.json.JSONArray(clusterMaxKhz))
        o.put("gov", org.json.JSONArray(governors))
        o.put("cpuUv", cpuUvDelta)
        o.put("gpuMin", gpuMinMhz)
        o.put("gpuMax", gpuMaxMhz)
        o.put("gpuGov", gpuGovernor)
        o.put("gpuUv", gpuUvDelta)
        o.put("thermal", thermalMode)
        return o.toString()
    }

    companion object {
        fun fromJson(s: String): OcConfig {
            val o = JSONObject(s)
            fun ints(k: String): List<Int> {
                val a = o.optJSONArray(k) ?: return listOf(-1, -1, -1)
                return (0 until a.length()).map { a.optInt(it, -1) }
            }
            fun strs(k: String): List<String> {
                val a = o.optJSONArray(k) ?: return listOf("schedutil", "schedutil", "schedutil")
                return (0 until a.length()).map { a.optString(it, "schedutil") }
            }
            return OcConfig(
                clusterMinKhz = ints("min"),
                clusterMaxKhz = ints("max"),
                governors = strs("gov"),
                cpuUvDelta = o.optInt("cpuUv", 0),
                gpuMinMhz = o.optInt("gpuMin", -1),
                gpuMaxMhz = o.optInt("gpuMax", -1),
                gpuGovernor = o.optString("gpuGov", "simple_ondemand"),
                gpuUvDelta = o.optInt("gpuUv", 0),
                thermalMode = o.optString("thermal", "")
            )
        }
    }
}

object OcPresets {
    val stock = OcConfig(
        clusterMinKhz = listOf(-1, -1, -1),
        clusterMaxKhz = listOf(-1, -1, -1),
        cpuUvDelta = 0, gpuUvDelta = 0
    )

    val gaming = OcConfig(
        // min freqs raised hard (auto-OC feel), max = table max (true OC on custom kernels)
        clusterMinKhz = listOf(-2, -2, -2),   // -2 = OC floor (45% / 60% / 65% of max)
        clusterMaxKhz = listOf(-1, -1, -1),
        cpuUvDelta = -10000,                  // -10 mV keeps the OC thermally sustainable
        gpuMinMhz = -2,                       // -2 = 70% of max
        gpuMaxMhz = -1,
        gpuGovernor = "performance",
        gpuUvDelta = -10000
    )

    val battery = OcConfig(
        clusterMinKhz = listOf(-1, -1, -1),   // table min
        clusterMaxKhz = listOf(-3, -3, -3),   // -3 = UC cap (80% / 85% / 85% of max)
        cpuUvDelta = -20000,
        gpuMinMhz = -1,
        gpuMaxMhz = -3,                       // -3 = 70% of max
        gpuGovernor = "simple_ondemand",
        gpuUvDelta = -15000
    )

    val sleep = OcConfig(
        clusterMinKhz = listOf(-1, -1, -1),
        clusterMaxKhz = listOf(-4, -4, -4),   // -4 = deep idle cap (60/65/70% of max)
        cpuUvDelta = 0,
        gpuMinMhz = -1,
        gpuMaxMhz = -4,                       // 50% of max
        gpuGovernor = "simple_ondemand",
        gpuUvDelta = 0
    )

    val all = linkedMapOf("Stock" to stock, "Overclock" to gaming, "Underclock" to battery, "Sleep" to sleep)
}
