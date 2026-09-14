package com.cameleonnbss.s20tuner.core

import kotlin.concurrent.thread
import java.util.concurrent.TimeUnit

data class ShellResult(val out: String, val err: String, val exitCode: Int, val timedOut: Boolean) {
    val ok: Boolean get() = exitCode == 0 && !timedOut
}

/**
 * Root shell runner. All privileged work goes through `su -c sh` with the
 * script fed on stdin, which avoids every quoting problem.
 */
object Shell {

    @Volatile var lastError: String = ""

    fun exec(script: String, timeoutMs: Long = 20000): ShellResult {
        return try {
            val proc = ProcessBuilder("su", "-c", "sh").start()
            proc.outputStream.use { os ->
                os.write(script.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = thread(start = true, isDaemon = true) {
                try { proc.inputStream.bufferedReader().forEachLine { out.appendLine(it) } } catch (_: Exception) {}
            }
            val tErr = thread(start = true, isDaemon = true) {
                try { proc.errorStream.bufferedReader().forEachLine { err.appendLine(it) } } catch (_: Exception) {}
            }
            val done = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) {
                proc.destroyForcibly()
                lastError = "shell timeout"
            }
            tOut.join(1500)
            tErr.join(1500)
            val code = try { proc.exitValue() } catch (_: Exception) { -1 }
            ShellResult(out.toString(), err.toString(), code, !done)
        } catch (e: Exception) {
            lastError = e.message ?: e.toString()
            ShellResult("", "no root / su failed: ${e.message}", -1, false)
        }
    }

    fun hasRoot(): Boolean {
        val r = exec("id -u; magisk -v 2>/dev/null | head -1", 8000)
        return r.out.trim().startsWith("0")
    }

    fun magiskVersion(): String {
        val r = exec("magisk -v 2>/dev/null | head -1", 5000)
        return r.out.trim().ifEmpty { "?" }
    }

    /** Read many sysfs/proc files in one shot. Output lines: P <path> then V <value> */
    fun readFiles(paths: List<String>, timeoutMs: Long = 12000): Map<String, String> {
        if (paths.isEmpty()) return emptyMap()
        val sb = StringBuilder()
        for (p in paths) {
            sb.append("echo P ").append(p).append('\n')
            sb.append("cat '").append(p.replace("'", "")).append("' 2>/dev/null | head -c 2048\n")
        }
        val r = exec(sb.toString(), timeoutMs)
        val map = HashMap<String, String>()
        var cur: String? = null
        val acc = StringBuilder()
        fun flush() {
            val k = cur
            if (k != null) map[k] = acc.toString().trim('\n', ' ').trim()
            acc.setLength(0)
        }
        for (line in r.out.lines()) {
            if (line.startsWith("P ")) {
                flush()
                cur = line.substring(2).trim()
            } else if (cur != null) {
                if (acc.isNotEmpty()) acc.append('\n')
                acc.append(line)
            }
        }
        flush()
        return map
    }

    fun runScript(script: String, timeoutMs: Long = 30000): ShellResult = exec(script, timeoutMs)
}
