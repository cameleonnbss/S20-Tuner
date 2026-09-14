#!/system/bin/sh
# 990 OC boot restore
(while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done)
sleep 12
{
  echo "=== 990 OC boot apply $(date) ==="
  if [ -f /data/adb/990oc/boot_apply.sh ]; then
    sh /data/adb/990oc/boot_apply.sh
    echo "exit=$?"
  else
    echo "no boot_apply.sh — enable 'Apply at boot' in the 990 OC app"
  fi
} > /data/local/tmp/990oc_boot.log 2>&1
