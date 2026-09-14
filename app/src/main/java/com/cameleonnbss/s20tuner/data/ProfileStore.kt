package com.cameleonnbss.s20tuner.data

import android.content.Context
import com.cameleonnbss.s20tuner.model.Profile
import com.cameleonnbss.s20tuner.model.TunerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Profiles are saved as JSON files in app-private storage, with an export
 * command that pushes them to /sdcard for backup / sharing.
 */
object ProfileStore {

    private fun dir(ctx: Context): File = File(ctx.filesDir, "profiles").apply { mkdirs() }

    fun list(ctx: Context): List<Profile> {
        return dir(ctx).listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                try {
                    val o = JSONObject(f.readText())
                    Profile(
                        name = o.optString("name", f.nameWithoutExtension),
                        config = TunerConfig.fromJson(o.getJSONObject("config").toString())
                    )
                } catch (_: Exception) { null }
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun save(ctx: Context, profile: Profile): Boolean {
        return try {
            val o = JSONObject()
            o.put("name", profile.name)
            o.put("config", JSONObject(profile.config.toJson()))
            File(dir(ctx), "${profile.name}.json").writeText(o.toString(2))
            true
        } catch (_: Exception) { false }
    }

    fun delete(ctx: Context, name: String): Boolean =
        File(dir(ctx), "$name.json").delete()

    /** Copy all profiles to /sdcard/S20Tuner/ so they survive uninstalls. */
    fun exportAll(ctx: Context): String {
        val dest = "/sdcard/S20Tuner"
        val sb = StringBuilder()
        sb.append("mkdir -p '$dest'\n")
        for (f in dir(ctx).listFiles { it.extension == "json" } ?: emptyArray()) {
            // Use cat heredoc-less: base64 encode for safe transfer
            val b64 = android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
            sb.append("echo '$b64' | base64 -d > '$dest/${f.name}'\n")
        }
        val r = Shell2.su(sb.toString())
        return if (r.first) "Exported to $dest" else "Export failed: ${r.second}"
    }

    /** Import all profiles from /sdcard/S20Tuner/. */
    fun importAll(ctx: Context): String {
        val r = Shell2.su("ls /sdcard/S20Tuner/*.json 2>/dev/null")
        if (!r.first) return "Nothing to import"
        val names = r.second.lines().filter { it.endsWith(".json") }
        var n = 0
        for (path in names) {
            val rr = Shell2.su("base64 '$path'")
            val content = try {
                String(android.util.Base64.decode(rr.second.trim(), android.util.Base64.NO_WRAP))
            } catch (_: Exception) { continue }
            try {
                val o = JSONObject(content)
                val name = o.optString("name", File(path).nameWithoutExtension)
                save(ctx, Profile(name, TunerConfig.fromJson(o.getJSONObject("config").toString())))
                n++
            } catch (_: Exception) {}
        }
        return "Imported $n profile(s)"
    }
}

/** Small indirection so ProfileStore can shell without exposing Shell everywhere. */
object Shell2 {
    fun su(script: String): Pair<Boolean, String> {
        val r = com.cameleonnbss.s20tuner.core.Shell.exec(script)
        return Pair(r.ok, if (r.ok) r.out else r.err)
    }
}
