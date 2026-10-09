#!/usr/bin/env bash
# ==============================================================================
# Mediatar Desktop Launcher & Icon Installer (Linux / Wayland / X11)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RESOURCES_DIR="${PROJECT_ROOT}/src/main/resources/com/nicch/mediatar"

APP_DIR="${HOME}/.local/share/applications"
ICON_BASE="${HOME}/.local/share/icons/hicolor"
DESKTOP_FILE="${APP_DIR}/mediatar.desktop"

echo "==> Installing Mediatar icons to user icon theme..."
mkdir -p "${APP_DIR}"

# Install multi-resolution icons to standard hicolor directory
for size in 16 32 48 64 128 256 512; do
    TARGET_DIR="${ICON_BASE}/${size}x${size}/apps"
    mkdir -p "${TARGET_DIR}"
    if [ -f "${RESOURCES_DIR}/icon_${size}.png" ]; then
        cp "${RESOURCES_DIR}/icon_${size}.png" "${TARGET_DIR}/mediatar.png"
    fi
done

# Install SVG scalable icon if supported
SVG_DIR="${ICON_BASE}/scalable/apps"
mkdir -p "${SVG_DIR}"
if [ -f "${RESOURCES_DIR}/icon.svg" ]; then
    cp "${RESOURCES_DIR}/icon.svg" "${SVG_DIR}/mediatar.svg"
fi

# Detect java binary
JAVA_BIN="$(which java || echo "/usr/bin/java")"
JAR_PATH="${PROJECT_ROOT}/target/Mediatar-1.0-SNAPSHOT.jar"

echo "==> Generating Desktop Entry at ${DESKTOP_FILE}..."
cat <<EOF > "${DESKTOP_FILE}"
[Desktop Entry]
Version=1.0
Type=Application
Name=MMediatar
GenericName=Video Editor
Comment=Lightweight JavaFX multi-track video and audio editor
Exec=${JAVA_BIN} -jar ${JAR_PATH}
Icon=mediatar
Terminal=false
Categories=AudioVideo;Video;AudioVideoEditing;
StartupNotify=true
StartupWMClass=com-nicch-mediatar-MMediatar
Keywords=video;audio;editor;timeline;ffmpeg;mediatar;
EOF

chmod +x "${DESKTOP_FILE}"

echo "==> Updating desktop and icon database..."
if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "${APP_DIR}" || true
fi

if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -f -t "${ICON_BASE}" 2>/dev/null || true
fi

echo "✅ Mediatar desktop entry and icons installed successfully."
echo "   Launchable via desktop launcher or: gtk-launch mediatar.desktop"
