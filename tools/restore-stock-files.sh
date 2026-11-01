#!/usr/bin/env bash
# Put the files derived from stock firmware back in the device tree. They aren't kept in Git.
# usage: restore-stock-files.sh SYSTEM_EXT_IMAGE
#
# The image is stock's system_ext logical partition. The results are byte-identical from run to run.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

"$here/extract-stock.sh" "$1" "$work/system_ext" >/dev/null
"$here/build-ims-apk.sh" "$work/system_ext"
