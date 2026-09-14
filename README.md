# 990 OC — Exynos 990 Overclock & Underclock

Minimal root app to hold the **Exynos 990** (Galaxy S20 series) at its maximum
frequencies, with underclock presets for battery. One screen, no clutter.

## Supported phones

Any **Exynos 990** Galaxy with Magisk root:

| Model | Codename |
|---|---|
| Galaxy S20 (Exynos) | `x1s` |
| Galaxy S20+ | `y2s` |
| Galaxy S20 Ultra | `z3s` |
| Galaxy Note 20 / Ultra (Exynos, some regions) | `c1s` / `c2s` |
| Galaxy S20 FE variants | `r8s` |

The app checks `ro.product.device` at launch and warns if the device is not
an Exynos 990 model. Snapdragon variants (SM-G98xU…) are **not** supported.

## How to use

1. Install the APK, open it, grant root when Magisk asks (or the boot module handles persistence only).
2. Tap **⚡ BEST** → all three CPU clusters are pinned at their max frequency with
   the `performance` governor (current = max, no dips).
3. Or pick a preset: **Stock** (default), **Underclock** (cooler, saves battery),
   **Sleep** (deep idle caps), or build your own with the sliders.
4. Every apply also writes `/data/adb/990oc_boot.sh` — flash the companion
   `990OC_Boot.zip` in Magisk and your tuning re-applies itself ~12 s after every boot.
5. Watch the **Live** tiles: current / max MHz per cluster + GPU, temp and load.

## What "overclock" means here (important)

- **On the stock kernel**: the kernel only registers Samsung's public frequency
  table — M5 prime maxes at **2730 MHz**, big at 2504, little at 2002, GPU at 800.
  The firmware (ECT) has hidden levels above that (prime 2834/3016, big 2600,
  GPU 832/897), but a stock kernel **cannot** use them. On stock, 990 OC pins
  you at the kernel maxes — full performance with zero throttling dips,
  which is what benchmarks/games notice.
- **With a kernel that unlocks the hidden OPPs** (e.g. Ragnarøk on XDA):
  the app auto-detects the new tables on launch — sliders, BEST and the Live
  tiles immediately reach 3016 / 2600 / 2106 MHz. The `fw 3016` badge on the
  tiles shows the firmware ceiling when it is higher than the kernel max.
- **Undervolt** requires a kernel exposing `vdd_levels` (Masonic/Ragnarøk
  style). On kernels without it, the slider is disabled — the app never
  pretends an apply worked when it can't.

## Build

```
gradle assembleRelease
```
APK + boot module land in `dist/` after signing (see repo releases for ready builds).
