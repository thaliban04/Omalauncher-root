#!/usr/bin/env bash
# fetch-droidspaces.sh — Downloads the DroidSpaces static binary from GitHub releases.
# Run this before building the app to include the DroidSpaces runtime in the APK assets.
# The binary is then deployed to /data/local/Droidspaces/bin/ on the device at first run.
#
# Usage: ./scripts/fetch-droidspaces.sh
# Output: app/src/main/assets/droidspaces-arm64

set -euo pipefail

DROIDSPACES_REPO="ravindu644/Droidspaces-OSS"
ASSETS_DIR="app/src/main/assets"
BINARY_NAME="droidspaces-arm64"
OUTPUT_PATH="$ASSETS_DIR/$BINARY_NAME"

# Fetch latest release tag from GitHub API
echo "Fetching latest DroidSpaces release..."
LATEST_TAG=$(curl -sf "https://api.github.com/repos/$DROIDSPACES_REPO/releases/latest" \
    | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

if [[ -z "$LATEST_TAG" ]]; then
    echo "Error: Could not fetch latest DroidSpaces release tag."
    echo "Check your internet connection or visit: https://github.com/$DROIDSPACES_REPO/releases"
    exit 1
fi

echo "Latest DroidSpaces version: $LATEST_TAG"

# Construct download URL for aarch64 binary
BINARY_URL="https://github.com/$DROIDSPACES_REPO/releases/download/$LATEST_TAG/droidspaces-aarch64"

echo "Downloading DroidSpaces binary from: $BINARY_URL"

# Ensure assets directory exists
mkdir -p "$ASSETS_DIR"

# Download binary
if curl -fL --progress-bar "$BINARY_URL" -o "$OUTPUT_PATH"; then
    chmod +x "$OUTPUT_PATH"
    echo ""
    echo "✓ DroidSpaces binary saved to: $OUTPUT_PATH"
    echo "  Size: $(du -sh "$OUTPUT_PATH" | cut -f1)"
    echo ""
    echo "Now build the app with: ./gradlew :app:installDebug"
else
    echo ""
    echo "Error: Failed to download DroidSpaces binary."
    echo "You can manually download it from:"
    echo "  https://github.com/$DROIDSPACES_REPO/releases"
    echo "  and place it at: $OUTPUT_PATH"
    exit 1
fi
