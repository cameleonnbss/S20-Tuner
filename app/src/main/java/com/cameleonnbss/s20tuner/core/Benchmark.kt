package com.cameleonnbss.s20tuner.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

data class BenchResult(
    val cpuScore: Int,        // higher = better (ops/sec normalized)
    val memScore: Int,
    val ioScore: Int,         // MB/s
    val timestamp: Long,
    val label: String
)

object Benchmark {

    suspend fun run(ctx: Context, label: String): BenchResult {
        val cpu = cpuBench()
        val mem = memBench()
        val io = ioBench(ctx)
        val res = BenchResult(cpu, mem, io, System.currentTimeMillis(), label)
        saveHistory(ctx, res)
        return res
    }

    /** SHA-256 over rotating buffers ~ fixed work, normalized to ops/s. */
    private fun cpuBench(): Int {
        val start = System.nanoTime()
        val md = MessageDigest.getInstance("SHA-256")
        var seed = 0x5eedL
        val buf = ByteArray(256)
        for (i in 0 until 2000) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            for (j in buf.indices step 8) {
                buf[j] = (seed ushr (j % 56)).toByte()
            }
            md.update(buf)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        if (elapsedMs <= 0) return 0
        return (2000.0 / elapsedMs * 1000).toInt()
    }

    /** Array copy + checksum bandwidth test. */
    private fun memBench(): Int {
        val start = System.nanoTime()
        val a = IntArray(1 shl 20) { it }
        val b = IntArray(1 shl 20)
        var acc = 0L
        for (r in 0 until 12) {
            System.arraycopy(a, 0, b, 0, a.size)
            for (i in b.indices step 1024) acc += b[i]
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        if (elapsedMs <= 0) return 0
        // MiB copied per second
        val mibCopied = (a.size * 4 / (1024.0 * 1024.0)) * 12 * 2
        return (mibCopied / elapsedMs * 1000).toInt() + (abs(acc) % 1).toInt()
    }

    /** Write+read 16MB in cacheDir, returns MB/s. */
    private fun ioBench(ctx: Context): Int {
        val f = File(ctx.cacheDir, "bench.tmp")
        return try {
            val data = ByteArray(1 shl 20) { (it % 251).toByte() }
            val t0 = System.nanoTime()
            f.outputStream().use { os ->
                repeat(16) { os.write(data) }
                os.fd.sync()
            }
            val t1 = System.nanoTime()
            f.inputStream().use { ins ->
                val md = MessageDigest.getInstance("MD5")
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(chunk)
                    if (n < 0) break
                    md.update(chunk, 0, n)
                }
            }
            val t2 = System.nanoTime()
            val writeMbS = 16.0 / ((t1 - t0) / 1e9)
            val readMbS = 16.0 / ((t2 - t1) / 1e9)
            ((writeMbS + readMbS) / 2).toInt()
        } catch (_: Exception) {
            0
        } finally {
            f.delete()
        }
    }

    private fun histFile(ctx: Context) = File(ctx.filesDir, "bench_history.json")

    fun history(ctx: Context): List<BenchResult> {
        val f = histFile(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BenchResult(
                    o.optInt("cpu"), o.optInt("mem"), o.optInt("io"),
                    o.optLong("ts"), o.optString("label")
                )
            }.sortedBy { it.timestamp }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveHistory(ctx: Context, r: BenchResult) {
        val list = history(ctx).toMutableList()
        list.add(r)
        if (list.size > 40) list.removeAt(0)
        val arr = JSONArray()
        for (x in list) {
            arr.put(JSONObject().apply {
                put("cpu", x.cpuScore); put("mem", x.memScore); put("io", x.ioScore)
                put("ts", x.timestamp); put("label", x.label)
            })
        }
        histFile(ctx).writeText(arr.toString())
    }
}
