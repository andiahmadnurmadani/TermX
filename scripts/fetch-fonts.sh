#!/usr/bin/env bash
# Mengunduh font terminal bawaan (rilis resmi di GitHub) ke app/src/main/res/font bila belum ada.
# Lisensi: JetBrains Mono, Fira Code, Cascadia Code = SIL OFL 1.1; Hack = MIT + Bitstream Vera.
set -euo pipefail
cd "$(dirname "$0")/.."
DEST=app/src/main/res/font
LIC=app/src/main/assets/licenses
mkdir -p "$DEST" "$LIC"
# Teks lisensi font (wajib ikut didistribusikan bersama font).
[ -f "$LIC/JetBrainsMono-OFL.txt" ] || curl -fsSL --retry 3 -o "$LIC/JetBrainsMono-OFL.txt" https://raw.githubusercontent.com/JetBrains/JetBrainsMono/v2.304/OFL.txt
[ -f "$LIC/FiraCode-OFL.txt" ] || curl -fsSL --retry 3 -o "$LIC/FiraCode-OFL.txt" https://raw.githubusercontent.com/tonsky/FiraCode/6.2/LICENSE
[ -f "$LIC/CascadiaCode-OFL.txt" ] || curl -fsSL --retry 3 -o "$LIC/CascadiaCode-OFL.txt" https://raw.githubusercontent.com/microsoft/cascadia-code/v2407.24/LICENSE
[ -f "$LIC/Hack-LICENSE.md" ] || curl -fsSL --retry 3 -o "$LIC/Hack-LICENSE.md" https://raw.githubusercontent.com/source-foundry/Hack/v3.003/LICENSE.md
if ls "$DEST"/*.ttf >/dev/null 2>&1 && [ "$(ls "$DEST"/*.ttf | wc -l)" -ge 8 ]; then
  echo "Font sudah ada, lewati."
  exit 0
fi
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
get() { curl -fsSL --retry 3 -o "$TMP/$1" "$2"; unzip -q -o "$TMP/$1" -d "$TMP/${1%.zip}"; }
get jb.zip https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip
get fira.zip https://github.com/tonsky/FiraCode/releases/download/6.2/Fira_Code_v6.2.zip
get cascadia.zip https://github.com/microsoft/cascadia-code/releases/download/v2407.24/CascadiaCode-2407.24.zip
get hack.zip https://github.com/source-foundry/Hack/releases/download/v3.003/Hack-v3.003-ttf.zip
cp "$TMP/jb/fonts/ttf/JetBrainsMono-Regular.ttf" "$DEST/jetbrains_mono_regular.ttf"
cp "$TMP/jb/fonts/ttf/JetBrainsMono-Bold.ttf" "$DEST/jetbrains_mono_bold.ttf"
cp "$TMP/fira/ttf/FiraCode-Regular.ttf" "$DEST/fira_code_regular.ttf"
cp "$TMP/fira/ttf/FiraCode-Bold.ttf" "$DEST/fira_code_bold.ttf"
cp "$TMP/cascadia/ttf/static/CascadiaCode-Regular.ttf" "$DEST/cascadia_code_regular.ttf"
cp "$TMP/cascadia/ttf/static/CascadiaCode-Bold.ttf" "$DEST/cascadia_code_bold.ttf"
cp "$TMP/hack/ttf/Hack-Regular.ttf" "$DEST/hack_regular.ttf"
cp "$TMP/hack/ttf/Hack-Bold.ttf" "$DEST/hack_bold.ttf"
echo "Font terpasang:"; ls -la "$DEST"
