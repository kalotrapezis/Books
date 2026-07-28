#!/usr/bin/env bash
# Installs books-sync for the current user and starts it watching the folder you
# already sync. No root, no system files: ~/.local/bin and a user service.
set -euo pipefail

FOLDER="${1:-}"
if [ -z "$FOLDER" ]; then
    echo "Usage: ./install.sh /path/to/the/folder/you/sync" >&2
    echo "  (the one holding one directory per book)" >&2
    exit 1
fi
FOLDER="$(cd "$FOLDER" && pwd)"

BIN="$HOME/.local/bin"
UNITS="$HOME/.config/systemd/user"
mkdir -p "$BIN" "$UNITS"

install -m 755 "$(dirname "$0")/books-sync" "$BIN/books-sync"

cat > "$UNITS/books-sync.service" <<EOF
[Unit]
Description=Sync Foliate's annotations with the Books folder for Android
After=default.target

[Service]
Type=simple
Environment=BOOKS_SYNC_FOLDER=$FOLDER
ExecStart=$BIN/books-sync --watch
Restart=on-failure
RestartSec=10

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now books-sync.service

echo
echo "Installed. It watches $FOLDER and starts with your session."
echo
echo "  status:  systemctl --user status books-sync"
echo "  log:     journalctl --user -u books-sync -f"
echo "  stop:    systemctl --user disable --now books-sync"
echo "  by hand: books-sync '$FOLDER' --dry-run"
