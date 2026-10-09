#!/usr/bin/env bash
# Build the Q25 image: lunch lineage_Q25-<release config>-<variant> and m bacon.
#
# Q1 must be built with no Sable layer staged; Q2+ requires it. The check stops a
# control build from silently including Sable bits (or the reverse).
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

RELEASE="${1:-}"
sable_require_release "$RELEASE"
sable_require_android_root

EXTRA="$SABLE_ANDROID_ROOT/vendor/extra/product.mk"
if sable_release_has_sable_layer "$RELEASE"; then
    [[ -f "$SABLE_ANDROID_ROOT/vendor/sable/q25/sable-q25.mk" && -f "$EXTRA" ]] ||
        sable_fail "Sable layer not staged; run: bash build/sable.sh q25 $RELEASE stage"
    grep -q "ro.sable.release=$RELEASE" "$SABLE_ANDROID_ROOT/vendor/sable/q25/release.mk" ||
        sable_fail "staged layer is for a different release; re-run stage for $RELEASE"
else
    [[ ! -e "$EXTRA" && ! -e "$SABLE_ANDROID_ROOT/vendor/sable/q25" ]] ||
        sable_fail "Q1 is the plain control build but a Sable layer is staged; run: bash build/sable.sh q25 Q1 stage"
fi

target="$(sable_lunch_target)"
[[ "$target" =~ ^${SABLE_Q25_LUNCH_PRODUCT}-[a-z0-9]+-(user|userdebug|eng)$ ]] || sable_fail "bad lunch target '$target'"
mkdir -p "$SABLE_EVIDENCE_ROOT"
log="$SABLE_EVIDENCE_ROOT/build-$RELEASE-$(sable_utc_stamp).log"
sable_log "lunch $target; m bacon (log: $log)"

export USE_CCACHE="${USE_CCACHE:-1}"
export CCACHE_DIR="$SABLE_CCACHE_DIR"
export CCACHE_EXEC="${CCACHE_EXEC:-$(command -v ccache || true)}"

cd "$SABLE_ANDROID_ROOT"
# envsetup.sh and lunch are not written for `set -eu`.
set +eu
# shellcheck source=/dev/null
source build/envsetup.sh
lunch "$target" || sable_fail "lunch $target failed"
m -j"$SABLE_JOBS" bacon 2>&1 | tee "$log"
status="${PIPESTATUS[0]}"
set -eu
[[ "$status" == 0 ]] || sable_fail "build failed (exit $status); see $log"

echo "BUILD=PASS"
echo "LUNCH=$target"
echo "OUT=$SABLE_ANDROID_ROOT/out/target/product/$SABLE_Q25_DEVICE"
echo "LOG=$log"
