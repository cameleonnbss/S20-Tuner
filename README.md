# 990 OC

Root app to **overclock / underclock the Exynos 990** (Galaxy S20 / S20+ / S20 Ultra, Note20).
Nothing else: CPU + GPU clocks, undervolt, thermal, auto mode, boot restore. Simple UI in the
spirit of GalaxyHz.

## What it does

- **Presets** — Stock / Overclock / Underclock / Sleep, one tap each
- **Custom tuning** — per-cluster (A55 little / A76 big / M5 prime) min & max frequency sliders,
  GPU (Mali-G77 MP11) max clock, **CPU undervolt** via the kernel `vdd_levels` table
  (Masonic/Ragnarøk-style kernels), GPU volt offset, thermal `sconfig`
- **Auto mode** — root daemon: load spike → Overclock, battery temp > 40 °C → Underclock,
  screen off → Sleep. Pre-generated idempotent scripts, only re-applies on state change
- **Apply at boot** — Magisk `service.d` script re-applies your config after reboot
  (module included in `magisk/`)

All writes are guarded (`[ -f node ] && echo x > node`) — missing kernel nodes are skipped,
nothing errors on kernels that don't expose a knob.

## Install

```
adb install 990OC.apk
```

Open once, grant the Magisk superuser prompt. Live values (per-cluster MHz, GPU MHz, temps,
load) update every 2 s.

## Build

```
gradle assembleRelease
```

Signed APKs are attached to the [Releases](../../releases).

## Compatibility

- Stock One UI kernels expose only min/max/governor — undervolt needs a custom kernel with a
  writable `vdd_levels` (the app detects and tells you).
- AOSP ROMs (Evolution X, LineageOS) work; app targets Android 11+.
