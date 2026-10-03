#!/usr/bin/env bash
#
# Wrap the jpackage app-image into a single-file AppImage.
#
# Usage: packaging/linux/make_appimage.sh [VERSION] [OUTPUT_DIR]
#   VERSION     app version (default: $ONAIR_VERSION, else 0.1.0)
#   OUTPUT_DIR  where the .AppImage is written
#               (default: build/compose/binaries/main/appimage)
#
# Requires: bash, curl, tar. The jpackage app-image is built automatically with
# `./gradlew createDistributable` if it is not already present. appimagetool is
# downloaded and cached under build/appimage/tools on first run.
set -euo pipefail

VERSION="${1:-${ONAIR_VERSION:-0.1.0}}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_IMAGE="$ROOT/build/compose/binaries/main/app/OnAir"
WORK_DIR="$ROOT/build/appimage"
APPDIR="$WORK_DIR/OnAir.AppDir"
OUT_DIR="${2:-$ROOT/build/compose/binaries/main/appimage}"
ARCH="${ARCH:-x86_64}"

if [[ ! -d "$APP_IMAGE" ]]; then
  echo "App image not found at $APP_IMAGE; building it with createDistributable..."
  (cd "$ROOT" && ./gradlew createDistributable -Pversion="$VERSION")
fi

echo "Assembling AppDir at $APPDIR"
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr"
cp -a "$APP_IMAGE/." "$APPDIR/usr/"

cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/OnAir" "$@"
EOF
chmod +x "$APPDIR/AppRun"

cp "$ROOT/packaging/icons/onair.png" "$APPDIR/onair.png"
cat > "$APPDIR/onair.desktop" <<'EOF'
[Desktop Entry]
Name=OnAir
Comment=System-tray tally light showing when your microphone or camera is in use
Exec=OnAir
Icon=onair
Type=Application
Categories=AudioVideo;
Terminal=false
EOF

TOOLS_DIR="$WORK_DIR/tools"
mkdir -p "$TOOLS_DIR"
TOOL="$TOOLS_DIR/appimagetool-$ARCH.AppImage"
if [[ ! -x "$TOOL" ]]; then
  echo "Downloading appimagetool..."
  curl -fsSL -o "$TOOL" \
    "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-$ARCH.AppImage"
  chmod +x "$TOOL"
fi

mkdir -p "$OUT_DIR"
OUTPUT="$OUT_DIR/OnAir-${VERSION}-${ARCH}.AppImage"
echo "Building $OUTPUT"
APPIMAGE_EXTRACT_AND_RUN=1 ARCH="$ARCH" "$TOOL" "$APPDIR" "$OUTPUT"
echo "Wrote $OUTPUT"
