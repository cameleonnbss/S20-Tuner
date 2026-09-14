#!/system/bin/sh
SKIPUNZIP=0
ui_print "**********************"
ui_print "  990 OC - Boot Restore"
ui_print "**********************"
ui_print "- Exynos 990 overclock / underclock persistence"
ui_print "- Enable 'Apply at boot' in the 990 OC app first"
ui_print "- Log: /data/local/tmp/990oc_boot.log"
set_perm_recursive "$MODPATH" 0 0 0755 0755
