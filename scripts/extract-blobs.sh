#!/usr/bin/env bash
# Optional: extract proprietary blobs from your own Q25 (or a stock dump) instead
# of using TheMuppets/proprietary_vendor_xelex_Q25 from the local manifest.
#
# Uses the LineageOS device tree's own extract-files.py. The extracted files land
# in vendor/xelex/Q25 inside the Android tree, never in this repository.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

usage() {
    cat >&2 <<'USAGE'
usage: scripts/extract-blobs.sh --from-device          (adb, device booted with root/adb access)
       scripts/extract-blobs.sh --from-dump PATH       (extracted stock firmware directory or zip)
USAGE
}

MODE="${1:-}"
sable_require_android_root
extractor="$SABLE_ANDROID_ROOT/device/xelex/Q25/extract-files.py"
[[ -f "$extractor" ]] || sable_fail "missing $extractor"

cd "$SABLE_ANDROID_ROOT/device/xelex/Q25"
case "$MODE" in
    --from-device) "$SABLE_PYTHON" "$extractor" ;;
    --from-dump) [[ -e "${2:-}" ]] || { usage; exit 64; }; "$SABLE_PYTHON" "$extractor" "$2" ;;
    *) usage; exit 64 ;;
esac

echo "EXTRACT=PASS"
echo "NOTE=blobs written to vendor/xelex/Q25; record the stock build in docs/STOCK_BASIS.md fields"
