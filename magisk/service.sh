#!/system/bin/sh
# 990 OC — restore your refresh rate after boot (only if the app saved one)
(
  sleep 12
  R=$(cat /data/adb/990oc_rate 2>/dev/null)
  if [ -n "$R" ]; then
    settings put system peak_refresh_rate "$R.0"
    settings put system min_refresh_rate "$R.0"
  fi
) &
