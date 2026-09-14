#!/system/bin/sh
SKIPUNZIP=0
ui_print "******************************"
ui_print "  S20 Tuner - Boot Persistence"
ui_print "******************************"
ui_print "- Re-applies your saved tuning at every boot"
ui_print "- Needs the S20 Tuner app to generate the config"
ui_print "- Log: /data/local/tmp/s20tuner_boot.log"
set_perm_recursive "$MODPATH" 0 0 0755 0755
set_perm "$MODPATH/service.sh" 0 0 0755
