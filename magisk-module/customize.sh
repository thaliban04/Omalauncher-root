SKIPUNZIP=0

ui_print "********************************************"
ui_print "*    OmaLauncher System Priv-App Module    *"
ui_print "*       Wayland + DroidSpaces Edition      *"
ui_print "********************************************"

ui_print "- Installing OmaLauncher to /system/priv-app..."

# Set proper permissions for system priv-app
set_perm_recursive "$MODPATH/system/priv-app/OmaLauncher" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/etc/permissions" 0 0 0755 0644

ui_print "- OmaLauncher set as privileged system application."
ui_print "- Anti-kill memory priority and full permissions enabled."
ui_print "- Done! Please reboot to activate."
