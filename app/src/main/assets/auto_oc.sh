#!/system/bin/sh
# S20 Tuner — auto overclock / underclock daemon
# PERF: screen on + heavy load  -> boosted min freqs + GPU performance
# BAL : screen on, light load   -> full clocks, schedutil, GPU on-demand
# ECO : screen off              -> capped clocks (battery saving)
# Config: /data/adb/s20tuner_auto.conf  (MODE=auto|gaming|battery, AUTO=1)

CONF=/data/adb/s20tuner_auto.conf
PIDF=/data/local/tmp/s20tuner_auto.pid
LOG=/data/local/tmp/s20tuner_auto.log
STATE=/data/local/tmp/s20tuner_auto.state
CPU=/sys/devices/system/cpu/cpufreq
KGSL=/sys/class/kgsl/kgsl-3d0
MALI=/sys/kernel/gpu

POLICIES=""
for p in 0 1 2 3 4 5 6 7; do
  [ -d $CPU/policy$p ] && POLICIES="$POLICIES policy$p"
done

pmax() { cat $CPU/$1/scaling_available_frequencies 2>/dev/null | tr ' ' '\n' | sort -n | tail -1; }
pmin() { cat $CPU/$1/scaling_available_frequencies 2>/dev/null | tr ' ' '\n' | sort -n | head -1; }

set_pol() { # pol min max gov
  [ -f $CPU/$1/scaling_governor ] && echo "$4" > $CPU/$1/scaling_governor 2>/dev/null
  [ -f $CPU/$1/scaling_max_freq ] && echo "$3" > $CPU/$1/scaling_max_freq 2>/dev/null
  [ -f $CPU/$1/scaling_min_freq ] && echo "$2" > $CPU/$1/scaling_min_freq 2>/dev/null
}

apply_state() {
  s=$1
  MODE=auto
  [ -f $CONF ] && . $CONF
  total=0
  for p in $POLICIES; do total=$((total+1)); done
  i=0
  for p in $POLICIES; do
    mx=$(pmax $p); mn=$(pmin $p)
    if [ -n "$mx" ]; then
      role=mid
      [ $i -eq 0 ] && role=little
      [ $i -eq $((total-1)) ] && role=prime
      case $s in
        perf)
          case $role in
            little) newmin=$((mx*45/100));;
            *)      newmin=$((mx*65/100));;
          esac
          [ "$MODE" != gaming ] && case $role in little) newmin=$((mx*35/100));; *) newmin=$((mx*55/100));; esac
          set_pol $p $newmin $mx schedutil
          ;;
        bal)
          newmax=$mx
          [ "$MODE" = battery ] && [ $role = little ] && newmax=$((mx*85/100))
          set_pol $p $mn $newmax schedutil
          ;;
        eco)
          case $role in
            little) newmax=$((mx*65/100));;
            mid)    newmax=$((mx*70/100));;
            prime)  newmax=$((mx*75/100));;
          esac
          [ "$MODE" = battery ] && newmax=$((newmax*9/10))
          set_pol $p $mn $newmax schedutil
          ;;
      esac
    fi
    i=$((i+1))
  done

  # GPU — Adreno (kgsl devfreq)
  if [ -d $KGSL/devfreq ]; then
    gmax=$(cat $KGSL/devfreq/available_frequencies 2>/dev/null | tr ' ' '\n' | sort -n | tail -1)
    [ -z "$gmax" ] && gmax=$(cat $KGSL/devfreq/max_freq 2>/dev/null)
    if [ -n "$gmax" ] && [ "$gmax" -gt 0 ] 2>/dev/null; then
      case $s in
        perf)
          [ -f $KGSL/devfreq/governor ] && echo performance > $KGSL/devfreq/governor 2>/dev/null
          echo $gmax > $KGSL/devfreq/max_freq 2>/dev/null
          [ -f $KGSL/devfreq/min_freq ] && echo $((gmax*70/100)) > $KGSL/devfreq/min_freq 2>/dev/null
          ;;
        bal)
          [ -f $KGSL/devfreq/governor ] && echo simple_ondemand > $KGSL/devfreq/governor 2>/dev/null
          echo $gmax > $KGSL/devfreq/max_freq 2>/dev/null
          [ -f $KGSL/devfreq/min_freq ] && echo $((gmax*15/100)) > $KGSL/devfreq/min_freq 2>/dev/null
          ;;
        eco)
          [ -f $KGSL/devfreq/governor ] && echo simple_ondemand > $KGSL/devfreq/governor 2>/dev/null
          echo $((gmax*55/100)) > $KGSL/devfreq/max_freq 2>/dev/null
          ;;
      esac
    fi
  fi

  # GPU — Mali (exynos990)
  if [ -f $MALI/gpu_max_clock ]; then
    mmax=$(cat $MALI/gpu_max_clock 2>/dev/null)
    if [ -n "$mmax" ] && [ "$mmax" -gt 0 ] 2>/dev/null; then
      case $s in
        perf)
          [ -f $MALI/gpu_min_clock ] && echo $((mmax*70/100)) > $MALI/gpu_min_clock 2>/dev/null
          echo $mmax > $MALI/gpu_max_clock 2>/dev/null
          ;;
        bal)
          [ -f $MALI/gpu_min_clock ] && echo $((mmax*15/100)) > $MALI/gpu_min_clock 2>/dev/null
          echo $mmax > $MALI/gpu_max_clock 2>/dev/null
          ;;
        eco)
          echo $((mmax*55/100)) > $MALI/gpu_max_clock 2>/dev/null
          ;;
      esac
    fi
  fi

  echo $s > $STATE
}

# one-shot restore (used when auto mode is switched OFF)
if [ "$1" = restore ]; then
  MODE=auto
  apply_state bal
  echo "$(date '+%H:%M:%S') daemon stopped, clocks restored" >> $LOG
  rm -f $PIDF
  exit 0
fi

echo $$ > $PIDF
echo "$(date '+%H:%M:%S') daemon started pid $$" >> $LOG
state=init

while :; do
  MODE=auto; LOAD_HI=250
  [ -f $CONF ] && . $CONF
  [ "$MODE" = gaming ] && LOAD_HI=120
  [ "$MODE" = battery ] && LOAD_HI=400

  awake=$(dumpsys power 2>/dev/null | grep -c 'mWakefulness=Awake')
  load=$(cut -d' ' -f1 /proc/loadavg 2>/dev/null | awk '{printf "%d", $1*100}')
  [ -z "$load" ] && load=0

  if [ "$awake" -eq 0 ]; then
    new=eco
  elif [ "$load" -ge "$LOAD_HI" ]; then
    new=perf
  elif [ "$state" = perf ] && [ "$load" -ge $((LOAD_HI-80)) ]; then
    new=perf
  else
    new=bal
  fi

  if [ "$new" != "$state" ]; then
    apply_state $new
    echo "$(date '+%H:%M:%S') state=$new load=$load" >> $LOG
    state=$new
  fi
  sleep 2
done
