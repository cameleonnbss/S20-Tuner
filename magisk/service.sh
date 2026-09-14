#!/system/bin/sh
# 990 OC — re-apply your saved overclock after boot
(
  sleep 12
  if [ -f /data/adb/990oc_boot.sh ]; then
    sh /data/adb/990oc_boot.sh >/dev/null 2>&1
  fi
) &
