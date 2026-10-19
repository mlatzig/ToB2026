#!/bin/sh
# ToB 2026 (Phase 7d): adds ToB 2026 to this Linux user's applications menu. Run it once from the unpacked folder,
# and again after moving the folder:
#   ./tob2026/install-desktop-entry.sh            writes ~/.local/share/applications/tob2026.desktop
#   ./tob2026/install-desktop-entry.sh --remove   removes that entry again
# A desktop entry needs absolute paths (it has none relative to its own file), so this script writes the folder it
# is in. It needs no root rights and changes nothing else.
set -eu
dir="$(cd "$(dirname "$0")" && pwd -P)"
case "${XDG_DATA_HOME:-}" in
    /*) data="$XDG_DATA_HOME" ;;
    *) data="$HOME/.local/share" ;;
esac
apps="$data/applications"
entry="$apps/tob2026.desktop"
if [ "${1:-}" = "--remove" ]; then
    rm -f "$entry"
    echo "Removed $entry"
    exit 0
fi
if [ $# -gt 0 ]; then
    echo "usage: $0 [--remove]" >&2
    exit 2
fi
if [ ! -x "$dir/bin/tob2026" ]; then
    echo "$dir/bin/tob2026 is missing or not executable: run this script from the unpacked ToB 2026 folder" >&2
    exit 1
fi
case "$dir" in
    *[\"\`\$\\%]*)
        echo "The folder $dir contains a character a desktop entry cannot hold (\" \` \$ \\ %): move it" >&2
        exit 1 ;;
esac
mkdir -p "$apps"
cat > "$entry.tmp" <<EOF
[Desktop Entry]
Type=Application
Version=1.0
Name=ToB 2026
GenericName=Puzzle game
Comment=Tower of Babel 2026 - a 3D remake of Pete Cooke's 1989 puzzle game
Exec="$dir/bin/tob2026"
Icon=$dir/lib/tob2026.png
Terminal=false
Categories=Game;LogicGame;
EOF
mv "$entry.tmp" "$entry"
chmod 644 "$entry"
if command -v update-desktop-database > /dev/null 2>&1; then
    update-desktop-database "$apps" > /dev/null 2>&1 || true
fi
echo "Wrote $entry: ToB 2026 is in the applications menu (Games)"
