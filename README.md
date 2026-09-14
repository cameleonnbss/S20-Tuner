# S20 Tuner

Root tuning app for the **Samsung Galaxy S20 series** (Exynos `x1s` / Snapdragon `y2s`), with a
**left rail of settings** (navigation rail on wide screens, scrollable tabs on phones) covering:

| Section | Knobs |
|---|---|
| **CPU** | per-cluster governors (little/mid/prime), min/max freq sliders, **undervolt** via Masonic `vdd_levels` table, thermal `sconfig` override |
| **GPU** | governor, min/max freq (Mali-G77 `gpu_max_clock` / Adreno 650 kgsl devfreq), voltage offset |
| **RAM** | ZRAM algorithm + size, swappiness, `dirty_ratio`, `vfs_cache_pressure`, **LMK minfree** presets |
| **Display** | 60/96/120 Hz forced refresh (bridges to your existing **GalaxyHz** module), SurfaceFlinger anti-flicker props, touch polling rate, **DC dimming**, color gamut (sRGB/DCI/native), glove mode |
| **Battery** | charge limit slider (80% longevity), fast-charge 25W/45W/off, idle-drain package suspends |
| **Storage/IO** | I/O scheduler (mq-deadline/bfq/kyber/none), `read_ahead_kb` |
| **Network** | TCP congestion (BBR/cubic/westwood/…), WiFi TX boost |
| **Debloat** | ~60 curated Samsung/Google/bloat packages, freeze (reversible) or remove per-user, one-tap **restore** |
| **Profiles** | save/load/delete JSON profiles, export/import via `/sdcard/S20Tuner`, presets (Gaming/Balanced/Eco) |
| **Benchmark** | CPU/MEM/IO scores with before→after comparison |
| **Logs** | app event log, `dmesg`, `logcat` |
| **Dashboard** | live CPU/GPU/temp/battery graphs, root + compatibility checks, boot-persistence switch |

## Install

```
adb install app/build/outputs/apk/release/app-release.apk
```

Open the app once and accept the Magisk superuser prompt. The Dashboard shows a root check and a
device compatibility banner (x1s/y2s are official; other devices still work in "guarded" mode).

## Boot persistence (two options)

1. **From the app**: Profiles → "Re-apply config at boot" — installs
   `/data/adb/service.d/s20tuner_boot.sh` which replays your saved config 8 s after boot.
2. **Magisk module**: flash `S20TunerBoot.zip` (built from `magisk/`) — same effect, visible in
   the Magisk app with a proper id/description. Both read `/data/adb/s20tuner/boot.sh`.

## Architecture

```
app/src/main/java/com/cameleonnbss/s20tuner/
├── core/
│   ├── Shell.kt          # su -c sh runner (stdin scripts, no quoting issues)
│   ├── Sysfs.kt          # all node paths + probe/poll script builders
│   ├── ApplyEngine.kt    # TunerConfig -> guarded idempotent sh script
│   ├── DeviceProbe.kt    # one-session probe: clusters, freq tables, governors
│   ├── BootInstaller.kt  # /data/adb/service.d persistence
│   └── Benchmark.kt      # CPU (SHA-256) / MEM (copy) / IO (fs) scores
├── model/TunerConfig.kt  # data class + JSON + presets
├── data/
│   ├── ProfileStore.kt   # JSON profiles + export/import
│   └── DebloatList.kt    # curated bloat catalog
└── ui/
    ├── TunerViewModel.kt # polling (1.5 s), apply, profiles, debloat
    ├── MainActivity.kt   # rail (landscape/tablet) + tabs (portrait)
    ├── Components.kt     # cards, sliders, chips, MiniGraph canvas
    └── screens/          # 12 screens
```

**Safety model**: every write is `echo X > node 2>/dev/null` — a missing node is skipped, never
erroring. Nothing touches boot partitions; the Magisk module only runs scripts.

## Build

Requires JDK 17+, Android SDK 36. Offline-friendly (deps cached):

```
./gradlew assembleRelease        # or gradle assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## Compatibility notes

- Frequencies/undervolt nodes are kernel-specific: stock OneUI kernels expose almost nothing;
  custom kernels (Masonic, StarScape…) expose `vdd_levels` etc. The app detects what exists.
- 96/120 Hz rely on your GalaxyHz module on AOSP ROMs (Evolution X, LineageOS); on OneUI the
  `user_refresh_rate` settings path is used.
- Charge-limit nodes vary by kernel; guarded writes mean unsupported kernels are no-ops.
