#!/bin/sh
# setup-wayland-droidspaces.sh
# Run inside the DroidSpaces container to configure Wayland forwarding to OmaLauncher.
#
# OmaLauncher acts as the Wayland compositor. This script sets up the container
# environment so Linux apps connect to OmaLauncher's Wayland socket.
#
# OmaLauncher exposes its Wayland socket at: /run/user/0/wayland-0 (in the container)
# which is bind-mounted from the Android side by the DroidSpaces backend.

set -e

echo "=== OmaLauncher Wayland Setup for DroidSpaces ==="

# Create XDG runtime directory if it doesn't exist
mkdir -p /run/user/0
chmod 700 /run/user/0

# Set up environment file that gets sourced on login
cat > /etc/environment.d/99-omalauncher-wayland.conf << 'EOF'
# OmaLauncher Wayland Display Configuration
# These vars point apps to OmaLauncher's Wayland compositor socket.
XDG_RUNTIME_DIR=/run/user/0
WAYLAND_DISPLAY=wayland-0
XDG_SESSION_TYPE=wayland
GDK_BACKEND=wayland
QT_QPA_PLATFORM=wayland
MOZ_ENABLE_WAYLAND=1
SDL_VIDEODRIVER=wayland
DISPLAY=:0
EOF

echo "✓ Wayland environment variables configured"

# Configure PAM to pick up environment
if [ -f /etc/pam.d/login ]; then
    if ! grep -q "pam_env.so" /etc/pam.d/login; then
        echo "session optional pam_env.so" >> /etc/pam.d/login
    fi
fi

# Set up systemd user session environment (if systemd is available)
if command -v systemctl > /dev/null 2>&1; then
    mkdir -p /etc/systemd/user/
    cat > /etc/systemd/user/omalauncher-wayland.conf << 'EOF'
[Service]
Environment=WAYLAND_DISPLAY=wayland-0
Environment=XDG_RUNTIME_DIR=/run/user/0
Environment=XDG_SESSION_TYPE=wayland
Environment=GDK_BACKEND=wayland
Environment=QT_QPA_PLATFORM=wayland
EOF
    echo "✓ Systemd user session configured"
fi

# Configure /etc/profile.d for shell sessions
cat > /etc/profile.d/omalauncher.sh << 'EOF'
# OmaLauncher integration: forward display to OmaLauncher's Wayland compositor
export XDG_RUNTIME_DIR=/run/user/0
export WAYLAND_DISPLAY=wayland-0
export XDG_SESSION_TYPE=wayland
export GDK_BACKEND=wayland
export QT_QPA_PLATFORM=wayland
export MOZ_ENABLE_WAYLAND=1
export SDL_VIDEODRIVER=wayland
EOF
chmod +x /etc/profile.d/omalauncher.sh

echo "✓ Shell profile configured"
echo ""
echo "=== Setup Complete ==="
echo "Linux apps in this container will now use OmaLauncher as Wayland compositor."
echo "Start a Wayland app: foot  (or any GTK/Qt/SDL app)"
