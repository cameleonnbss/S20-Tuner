#!/system/bin/sh
# S20 Tuner boot persistence - runs after boot completed.
MODDIR=${0%/*}

# Wait for boot to finish
(while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done)
sleep 8

LOG=/data/local/tmp/s20tuner_boot.log
{
  echo "=== S20 Tuner boot apply $(date) ==="
  if [ -f /data/adb/s20tuner/boot.sh ]; then
    sh /data/adb/s20tuner/boot.sh
    echo "exit=$?"
  else
    echo "no /data/adb/s20tuner/boot.sh — open the S20 Tuner app and enable Boot persistence"
  fi
} > "$LOG" 2>&1
