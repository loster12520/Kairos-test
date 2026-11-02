#!/usr/bin/env bash
# Installs the shared libraries Playwright's Chromium needs *without* root, by unpacking the
# Ubuntu packages into ~/.local/lib/kairos-browser-deps. Run it once on a bare container/WSL
# image where `npx playwright install --with-deps` is not possible.
set -euo pipefail

DEST="${KAIROS_BROWSER_DEPS:-$HOME/.local/lib/kairos-browser-deps}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "downloading browser runtime packages into $DEST"
(cd "$TMP" && apt-get download libnspr4 libnss3 libasound2t64)

mkdir -p "$DEST"
for package in "$TMP"/*.deb; do
  dpkg-deb -x "$package" "$DEST"
done

echo "installed:"
ls "$DEST/usr/lib/x86_64-linux-gnu" | head
echo
echo "run checks with:"
echo "  export LD_LIBRARY_PATH=\"$DEST/usr/lib/x86_64-linux-gnu\${LD_LIBRARY_PATH:+:\$LD_LIBRARY_PATH}\""
