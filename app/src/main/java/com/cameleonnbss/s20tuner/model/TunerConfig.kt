package com.cameleonnbss.s20tuner.model

import org.json.JSONObject

/**
 * Every knob the app exposes. Defaults = "leave node untouched" (empty strings
 * / -1 sentinels), so applying a fresh config changes nothing until you touch it.
 */
data class TunerConfig(
    // CPU — one entry per cluster in detection order (little / mid / prime)
    val cpuGovernors: List<String> = listOf("", "", ""),
    val cpuMinFreqs: List<Int> = listOf(-1, -1, -1),
    val cpuMaxFreqs: List<Int> = listOf(-1, -1, -1),
    // Masonic undervolt: freq_khz -> microvolt delta (negative = undervolt), empty = off
    val cpuUvDeltas: Map<String, Int> = emptyMap(),

    // GPU
    val gpuGovernor: String = "",
    val gpuMaxFreq: Int = -1,
    val gpuMinFreq: Int = -1,
    val gpuUvOffset: Int = 0,          // mV offset applied to Adreno gx levels / Mali table

    // Thermal
    val thermalMode: String = "",      // sconfig: 0 normal, 1/2/3... cooling; 9 = hot override on some kernels
    val thermalOverride: Boolean = false,

    // Memory
    val zramAlgo: String = "",
    val zramSizeMb: Int = -1,
    val swappiness: Int = -1,
    val dirtyRatio: Int = -1,
    val dirtyBgRatio: Int = -1,
    val vfsCachePressure: Int = -1,
    val lmkMinfree: String = "",       // comma separated 6 values

    // Storage
    val ioScheduler: String = "",
    val readAheadKb: Int = -1,

    // Network
    val tcpAlgo: String = "",
    val wifiTxBoost: Boolean = false,

    // Display
    val refreshRate: Int = -1,         // 60 / 96 / 120 ; -1 untouched
    val lockRefreshRate: Boolean = false, // kills SF idle timers (GalaxyHz anti-flicker)
    val touchPollRate: Int = -1,       // Hz
    val dcDimming: Boolean? = null,
    val colorGamut: String = "",       // "srgb", "dci", "native"
    val touchSensitivity: Boolean? = null, // glove mode

    // Battery
    val chargeLimit: Int = -1,         // percent, -1 untouched
    val fastCharge: String = "",       // "25", "45", "off"
    val blockWakelocks: List<String> = emptyList(),

    // Misc
    val extraProps: Map<String, String> = emptyMap()
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("cpuGovernors", org.json.JSONArray(cpuGovernors))
        o.put("cpuMinFreqs", org.json.JSONArray(cpuMinFreqs))
        o.put("cpuMaxFreqs", org.json.JSONArray(cpuMaxFreqs))
        val uv = JSONObject()
        for ((k, v) in cpuUvDeltas) uv.put(k, v)
        o.put("cpuUvDeltas", uv)
        o.put("gpuGovernor", gpuGovernor)
        o.put("gpuMaxFreq", gpuMaxFreq)
        o.put("gpuMinFreq", gpuMinFreq)
        o.put("gpuUvOffset", gpuUvOffset)
        o.put("thermalMode", thermalMode)
        o.put("thermalOverride", thermalOverride)
        o.put("zramAlgo", zramAlgo)
        o.put("zramSizeMb", zramSizeMb)
        o.put("swappiness", swappiness)
        o.put("dirtyRatio", dirtyRatio)
        o.put("dirtyBgRatio", dirtyBgRatio)
        o.put("vfsCachePressure", vfsCachePressure)
        o.put("lmkMinfree", lmkMinfree)
        o.put("ioScheduler", ioScheduler)
        o.put("readAheadKb", readAheadKb)
        o.put("tcpAlgo", tcpAlgo)
        o.put("wifiTxBoost", wifiTxBoost)
        o.put("refreshRate", refreshRate)
        o.put("lockRefreshRate", lockRefreshRate)
        o.put("touchPollRate", touchPollRate)
        if (dcDimming != null) o.put("dcDimming", dcDimming)
        o.put("colorGamut", colorGamut)
        if (touchSensitivity != null) o.put("touchSensitivity", touchSensitivity)
        o.put("chargeLimit", chargeLimit)
        o.put("fastCharge", fastCharge)
        o.put("blockWakelocks", org.json.JSONArray(blockWakelocks))
        val props = JSONObject()
        for ((k, v) in extraProps) props.put(k, v)
        o.put("extraProps", props)
        return o.toString(2)
    }

    companion object {
        fun fromJson(s: String): TunerConfig {
            val o = JSONObject(s)
            fun strArr(name: String): List<String> {
                val a = o.optJSONArray(name) ?: return listOf("", "", "")
                return (0 until a.length()).map { a.optString(it, "") }
            }
            fun intArr(name: String): List<Int> {
                val a = o.optJSONArray(name) ?: return listOf(-1, -1, -1)
                return (0 until a.length()).map { a.optInt(it, -1) }
            }
            val uv = HashMap<String, Int>()
            o.optJSONObject("cpuUvDeltas")?.let { jo ->
                for (k in jo.keys()) uv[k] = jo.optInt(k, 0)
            }
            val props = HashMap<String, String>()
            o.optJSONObject("extraProps")?.let { jo ->
                for (k in jo.keys()) props[k] = jo.optString(k, "")
            }
            val wls = ArrayList<String>()
            o.optJSONArray("blockWakelocks")?.let { a -> for (i in 0 until a.length()) wls.add(a.optString(i)) }
            return TunerConfig(
                cpuGovernors = strArr("cpuGovernors"),
                cpuMinFreqs = intArr("cpuMinFreqs"),
                cpuMaxFreqs = intArr("cpuMaxFreqs"),
                cpuUvDeltas = uv,
                gpuGovernor = o.optString("gpuGovernor", ""),
                gpuMaxFreq = o.optInt("gpuMaxFreq", -1),
                gpuMinFreq = o.optInt("gpuMinFreq", -1),
                gpuUvOffset = o.optInt("gpuUvOffset", 0),
                thermalMode = o.optString("thermalMode", ""),
                thermalOverride = o.optBoolean("thermalOverride", false),
                zramAlgo = o.optString("zramAlgo", ""),
                zramSizeMb = o.optInt("zramSizeMb", -1),
                swappiness = o.optInt("swappiness", -1),
                dirtyRatio = o.optInt("dirtyRatio", -1),
                dirtyBgRatio = o.optInt("dirtyBgRatio", -1),
                vfsCachePressure = o.optInt("vfsCachePressure", -1),
                lmkMinfree = o.optString("lmkMinfree", ""),
                ioScheduler = o.optString("ioScheduler", ""),
                readAheadKb = o.optInt("readAheadKb", -1),
                tcpAlgo = o.optString("tcpAlgo", ""),
                wifiTxBoost = o.optBoolean("wifiTxBoost", false),
                refreshRate = o.optInt("refreshRate", -1),
                lockRefreshRate = o.optBoolean("lockRefreshRate", false),
                touchPollRate = o.optInt("touchPollRate", -1),
                dcDimming = if (o.has("dcDimming")) o.optBoolean("dcDimming") else null,
                colorGamut = o.optString("colorGamut", ""),
                touchSensitivity = if (o.has("touchSensitivity")) o.optBoolean("touchSensitivity") else null,
                chargeLimit = o.optInt("chargeLimit", -1),
                fastCharge = o.optString("fastCharge", ""),
                blockWakelocks = wls,
                extraProps = props
            )
        }
    }
}

data class Profile(val name: String, val config: TunerConfig, val isAuto: Boolean = false)

object Presets {
    val balanced = Profile("Balanced", TunerConfig(
        cpuGovernors = listOf("schedutil", "schedutil", "schedutil"),
        zramAlgo = "lz4", swappiness = 120, vfsCachePressure = 100,
        dirtyRatio = 20, dirtyBgRatio = 5,
        lmkMinfree = "25600,51200,76800,102400,128000,153600",
        ioScheduler = "mq-deadline", readAheadKb = 512,
        tcpAlgo = "bbr", refreshRate = 96, lockRefreshRate = true
    ))

    val gaming = Profile("Gaming", TunerConfig(
        cpuGovernors = listOf("schedutil", "performance", "performance"),
        cpuMaxFreqs = listOf(-1, 2600000, 2730000),
        gpuGovernor = "performance",
        thermalOverride = false,
        zramAlgo = "lz4", swappiness = 60,
        ioScheduler = "mq-deadline", readAheadKb = 1024,
        tcpAlgo = "bbr", refreshRate = 120, lockRefreshRate = true,
        touchPollRate = 240, dcDimming = false
    ))

    val battery = Profile("Battery Eco", TunerConfig(
        cpuGovernors = listOf("powersave", "powersave", "powersave"),
        cpuMaxFreqs = listOf(1600000, 2000000, 2100000),
        zramAlgo = "zstd", zramSizeMb = 4096, swappiness = 160,
        vfsCachePressure = 200,
        lmkMinfree = "32000,64000,96000,128000,160000,192000",
        ioScheduler = "bfq", readAheadKb = 256,
        tcpAlgo = "westwood", refreshRate = 60,
        chargeLimit = 80, dcDimming = true
    ))

    val allPresets = listOf(balanced, gaming, battery)
}
