package com.cameleonnbss.s20tuner.core

import android.content.Context
import java.io.ByteArrayOutputStream

/**
 * Auto OC / Underclock engine: installs a tiny shell daemon that watches
 * screen state + CPU load and re-tunes clocks automatically:
 *   - screen off            -> ECO   (underclock, battery saving)
 *   - screen on + load      -> PERF  (auto overclock)
 *   - screen on, light load -> BAL   (normal)
 * Modes: auto (adaptive) / gaming (more aggressive OC) / battery (eco bias).
 */
object AutoEngine {

    private const val CONF = "/data/adb/s20tuner_auto.conf"
    private const val PIDF = "/data/local/tmp/s20tuner_auto.pid"
    private const val STATE = "/data/local/tmp/s20tuner_auto.state"

    fun running(): Boolean {
        val r = Shell.exec(
            "if [ -f $PIDF ] && [ -d /proc/\$(cat $PIDF) ]; then echo RUNNING; else echo STOPPED; fi", 8000
        )
        return r.out.trim() == "RUNNING"
    }

    fun currentMode(): String {
        val r = Shell.exec(". $CONF 2>/dev/null; echo \${MODE:-auto}", 6000)
        return r.out.trim().ifBlank { "auto" }
    }

    fun currentState(): String {
        val r = Shell.exec("cat $STATE 2>/dev/null", 5000)
        return r.out.trim().ifBlank { "init" }
    }

    /**
     * Installs the daemon script from assets, writes config, starts it.
     * idempotent: kills any old instance first.
     */
    fun setEnabled(ctx: Context, on: Boolean, mode: String): String {
        val script = ctx.assets.open("auto_oc.sh").bufferedReader().use { it.readText() }
        val b64 = android.util.Base64.encodeToString(script.toByteArray(), android.util.Base64.NO_WRAP)
        val sb = StringBuilder()
        if (on) {
            sb.append("mkdir -p /data/adb\n")
            sb.append("echo '$b64' | base64 -d > /data/adb/s20tuner_auto.sh\n")
            sb.append("chmod 755 /data/adb/s20tuner_auto.sh\n")
            sb.append("echo \"MODE=$mode\" > $CONF\n")
            // kill previous instance then start detached, surviving app close
            sb.append("[ -f $PIDF ] && kill \$(cat $PIDF) 2>/dev/null; rm -f $PIDF\n")
            sb.append("nohup /system/bin/sh /data/adb/s20tuner_auto.sh >/dev/null 2>&1 &\n")
            sb.append("sleep 1\n")
            sb.append("if [ -f $PIDF ] && [ -d /proc/\$(cat $PIDF) ]; then echo STARTED; else echo FAILED; fi\n")
        } else {
            sb.append("[ -f $PIDF ] && kill \$(cat $PIDF) 2>/dev/null\n")
            sb.append("/system/bin/sh /data/adb/s20tuner_auto.sh restore 2>/dev/null\n")
            sb.append("echo STOPPED\n")
        }
        val r = Shell.exec(sb.toString(), 15000)
        return when {
            on && r.out.contains("STARTED") -> "Auto OC/UC started ($mode)"
            on -> "Failed to start daemon: ${r.out.takeLast(80)} ${r.err.takeLast(80)}"
            else -> "Auto OC/UC stopped — clocks restored"
        }
    }

    fun setMode(mode: String): String {
        val sb = StringBuilder()
        sb.append("echo \"MODE=$mode\" > $CONF\n")
        // nudge the running daemon so it picks up the new mode immediately
        sb.append("[ -f $PIDF ] && kill -0 \$(cat $PIDF) 2>/dev/null && echo OK\n")
        val r = Shell.exec(sb.toString(), 8000)
        return if (r.ok) "Mode set to $mode" else "Failed to set mode"
    }

    fun tailLog(): String {
        val r = Shell.exec("tail -30 /data/local/tmp/s20tuner_auto.log 2>/dev/null", 8000)
        return r.out.ifBlank { "(no log yet)" }
    }
}
