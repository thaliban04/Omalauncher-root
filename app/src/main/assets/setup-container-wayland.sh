#!/bin/sh
# setup-container-wayland.sh
# Bundled in OmaLauncher APK assets. Deployed and executed inside DroidSpaces container
# to configure Wayland display forwarding to OmaLauncher's wlroots compositor.

set -e

RUNTIME_DIR=/run/user/0
WAYLAND_SOCKET="$RUNTIME_DIR/wayland-0"

# Create runtime directory
mkdir -p "$RUNTIME_DIR"
chmod 700 "$RUNTIME_DIR"

# Profile script for all shells
mkdir -p /etc/profile.d
cat > /etc/profile.d/99-omalauncher.sh << 'PROFILE'
export XDG_RUNTIME_DIR=/run/user/0
export WAYLAND_DISPLAY=wayland-0
export XDG_SESSION_TYPE=wayland
export GDK_BACKEND=wayland
export QT_QPA_PLATFORM=wayland
export MOZ_ENABLE_WAYLAND=1
export SDL_VIDEODRIVER=wayland
PROFILE
chmod +x /etc/profile.d/99-omalauncher.sh

# For bash/zsh users
for rc in /root/.bashrc /root/.zshrc; do
    if [ -f "$rc" ]; then
        if ! grep -q "omalauncher" "$rc" 2>/dev/null; then
            echo "" >> "$rc"
            echo "# OmaLauncher Wayland" >> "$rc"
            echo "source /etc/profile.d/99-omalauncher.sh" >> "$rc"
        fi
    fi
done

echo "OmaLauncher Wayland configuration complete."
echo "Wayland socket: $WAYLAND_SOCKET"
