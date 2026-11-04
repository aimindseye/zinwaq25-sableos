#!/usr/bin/env bash
# Install the microG add-on on a Q25 running SableOS 2.0 (no Google by default).
#
#   bash scripts/install-microg-addon.sh                install microG Services and Companion
#   bash scripts/install-microg-addon.sh --with-gsf     also the Services Framework Proxy
#   bash scripts/install-microg-addon.sh --with-aurora  also Aurora Store (Play Store apps)
#   bash scripts/install-microg-addon.sh --check        only verify the APKs and the phone
#
# Every APK is one of the official files pinned by sha256 in product/q25/microg/prebuilts.tsv
# (GmsCore, FakeStore) and addon.tsv (GsfProxy, AuroraStore). Fetch them first:
#   NETWORK_FETCH_AUTHORIZED=YES bash scripts/fetch-microg.sh
#   SABLE_MICROG_PREBUILTS=product/q25/microg/addon.tsv NETWORK_FETCH_AUTHORIZED=YES bash scripts/fetch-microg.sh
# The script checks the hashes again, checks that the phone allows microG's signature
# spoofing (ro.sable.microg.spoofing=true, or a debuggable build), installs the APKs in
# order with adb as normal apps, and grants the runtime permissions microG needs.
# It changes nothing on the computer. docs/MICROG.md explains each APK.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

GSF=NO AURORA=NO CHECK=NO
while (($#)); do
    case "$1" in
        --with-gsf) GSF=YES; shift ;;
        --with-aurora) AURORA=YES; shift ;;
        --check) CHECK=YES; shift ;;
        -h|--help) sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) sable_fail "unknown argument $1" ;;
    esac
done

ADB="${ADB:-adb}"
CACHE="$SABLE_MICROG_CACHE"
CORE_TSV="${SABLE_MICROG_PREBUILTS:-$SABLE_REPO_ROOT/product/q25/microg/prebuilts.tsv}"
ADDON_TSV="${SABLE_MICROG_ADDON:-$SABLE_REPO_ROOT/product/q25/microg/addon.tsv}"

# 1. The APKs: the same sha256 check the build uses.
SABLE_MICROG_PREBUILTS="$CORE_TSV" bash "$SABLE_REPO_ROOT/scripts/fetch-microg.sh" --check >/dev/null
modules=(GmsCore FakeStore)
if [[ "$GSF" == YES || "$AURORA" == YES ]]; then
    SABLE_MICROG_PREBUILTS="$ADDON_TSV" bash "$SABLE_REPO_ROOT/scripts/fetch-microg.sh" --check >/dev/null
    [[ "$GSF" == YES ]] && modules+=(GsfProxy)
    [[ "$AURORA" == YES ]] && modules+=(AuroraStore)
fi
echo "ADDON_APKS=PASS ${modules[*]}"

# 2. The phone: one device, booted, and spoofing allowed for microG.
command -v "$ADB" >/dev/null || sable_fail "adb not found (install Android platform-tools)"
"$ADB" get-state >/dev/null 2>&1 || sable_fail "no phone on adb: connect the Q25 with USB debugging on"
prop() { "$ADB" shell getprop "$1" </dev/null | tr -d '\r'; }
[[ "$(prop sys.boot_completed)" == 1 ]] || sable_fail "the phone has not finished booting"
spoof="$(prop ro.sable.microg.spoofing)" debuggable="$(prop ro.debuggable)"
if [[ "$spoof" != true && "$debuggable" != 1 ]]; then
    sable_fail "this image does not allow microG's signature spoofing (ro.sable.microg.spoofing is not true); microG would not work"
fi
if [[ "$(prop ro.sable.microg)" == true ]]; then
    sable_fail "microG is already built into this image; nothing to install"
fi
echo "PHONE=PASS spoofing=${spoof:-unset} debuggable=${debuggable:-0}"
[[ "$CHECK" == YES ]] && exit 0

# 3. Install, in order: Services first, so Companion and the rest find it.
for m in "${modules[@]}"; do
    extra=()
    # GsfProxy targets an old SDK, which Android 14 and later refuse without this flag.
    [[ "$m" == GsfProxy ]] && extra=(--bypass-low-target-sdk-block)
    sable_log "installing $m"
    "$ADB" install -r "${extra[@]}" "$CACHE/$m.apk" </dev/null || sable_fail "$m did not install"
done

# 4. The runtime grants a built-in microG gets at first boot (default-permissions-sable-microg.xml).
grant() { "$ADB" shell pm grant "$1" "$2" </dev/null >/dev/null 2>&1 || echo "NOT_GRANTED $1 $2"; }
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION GET_ACCOUNTS READ_PHONE_STATE POST_NOTIFICATIONS; do
    grant com.google.android.gms "android.permission.$p"
done
for p in GET_ACCOUNTS POST_NOTIFICATIONS; do grant com.android.vending "android.permission.$p"; done

echo "ADDON_INSTALL=PASS ${modules[*]}"
echo "Next: open microG Settings > Self-Check and tick what it asks (battery optimisation, then"
echo "Google device registration and Cloud Messaging if you want push). docs/MICROG.md has the steps."
