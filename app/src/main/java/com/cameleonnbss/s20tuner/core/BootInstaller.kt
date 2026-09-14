package com.cameleonnbss.s20tuner.core

import com.cameleonnbss.s20tuner.model.TunerConfig

/**
 * Installs a boot script into /data/adb/service.d/ (Magisk standard location)
 * that re-applies the saved config at every boot. This gives persistence
 * without needing a full Magisk module.
 */
object BootInstaller {

    private val dir = Sysfs.TUNER_DIR
    private val servicePath = "/data/adb/service.d/s20tuner_boot.sh"

    fun install(configJson: String, enable: Boolean): String {
        val apply = ApplyEngine.buildScript(TunerConfig.fromJson(configJson), Sysfs.cpuPolicyPaths())
        val sb = StringBuilder()
        sb.append("mkdir -p '$dir'\n")
        sb.append("mkdir -p /data/adb/service.d\n")

        // stored config snapshot
        val b64 = android.util.Base64.encodeToString(configJson.toByteArray(), android.util.Base64.NO_WRAP)
        sb.append("echo '$b64' | base64 -d > '$dir/config.json'\n")

        // boot script: waits for boot complete, then applies via the app's engine
        sb.append("cat > '$dir/boot.sh' << 'S20EOF'\n")
        sb.append(apply)
        sb.append("S20EOF\n")

        // service.d wrapper
        val wrapper = """
            #!/system/bin/sh
            # S20 Tuner boot persistence
            (while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
             sleep 8
             sh '$dir/boot.sh' > /data/local/tmp/s20tuner_boot.log 2>&1) &
        """.trimIndent()
        sb.append("echo '$b64' > /dev/null\n") // noop keep-session warm
        sb.append("printf '%s\\n' " + wrapper.lines().joinToString(" ") { "'$it'" } + " > '$servicePath'\n")
        sb.append("chmod 755 '$servicePath' '$dir/boot.sh'\n")

        if (!enable) {
            sb.append("rm -f '$servicePath'\n")
        }
        val r = Shell.exec(sb.toString())
        return if (r.ok) "Boot persistence " + if (enable) "enabled" else "removed" else "Failed: ${r.err}"
    }

    fun status(): Boolean {
        val r = Shell.exec("[ -f '$servicePath' ] && echo YES || echo NO", 8000)
        return r.out.trim() == "YES"
    }

    fun remove(): String {
        val r = Shell.exec("rm -f '$servicePath' '$dir/boot.sh'", 8000)
        return if (r.ok) "Boot script removed" else "Failed: ${r.err}"
    }
}
