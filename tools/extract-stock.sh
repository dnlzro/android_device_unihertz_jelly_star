#!/usr/bin/env bash
# Unpack a stock EROFS image into a new folder, e.g. system_ext for build-ims-apk.sh.
# usage: extract-stock.sh IMAGE OUT_DIR
set -euo pipefail
nix shell nixpkgs#erofs-utils -c fsck.erofs --extract="$2" --no-preserve "$1"
