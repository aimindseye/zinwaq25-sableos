#!/usr/bin/env bash
# Check that this host can build SableOS for the Q25. Read-only.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

status=0
check() {
    local name="$1" ok="$2" detail="$3"
    if [[ "$ok" == yes ]]; then
        printf 'PASS  %-22s %s\n' "$name" "$detail"
    else
        printf 'FAIL  %-22s %s\n' "$name" "$detail"
        status=1
    fi
}
warn() {
    printf 'WARN  %-22s %s\n' "$1" "$2"
}

os="$(uname -s)/$(uname -m)"
[[ "$os" == Linux/x86_64 ]] && check host-os yes "$os" || check host-os no "$os (AOSP builds need Linux x86_64)"

# Round to the nearest GiB: a 16 GB machine reports a little under 16 GiB.
mem_gib=$(( ($(awk '/MemTotal/ {print $2}' /proc/meminfo 2>/dev/null || echo 0) + 524288) / 1048576 ))
if (( mem_gib >= 32 )); then
    check memory yes "${mem_gib} GiB"
elif (( mem_gib >= 16 )); then
    warn memory "${mem_gib} GiB (works with swap/zram; 32 GiB+ recommended)"
else
    check memory no "${mem_gib} GiB (16 GiB minimum)"
fi

mkdir -p "$SABLE_BUILD_ROOT" 2>/dev/null || true
free_gib=$(( $(df -Pk "$SABLE_BUILD_ROOT" 2>/dev/null | awk 'NR==2 {print $4}' || echo 0) / 1024 / 1024 ))
(( free_gib >= SABLE_BUILD_MIN_FREE_GIB )) &&
    check disk yes "${free_gib} GiB free at $SABLE_BUILD_ROOT" ||
    check disk no "${free_gib} GiB free at $SABLE_BUILD_ROOT (need ${SABLE_BUILD_MIN_FREE_GIB})"

for tool in git "$SABLE_REPO_TOOL" "$SABLE_PYTHON" curl zip unzip rsync; do
    if command -v "$tool" >/dev/null 2>&1; then
        check "tool:$tool" yes "$(command -v "$tool")"
    else
        check "tool:$tool" no "not on PATH"
    fi
done

if command -v git-lfs >/dev/null 2>&1; then
    check tool:git-lfs yes "$(command -v git-lfs)"
else
    check tool:git-lfs no "LineageOS needs git-lfs"
fi

for tool in "$SABLE_ADB" "$SABLE_FASTBOOT" ccache; do
    command -v "$tool" >/dev/null 2>&1 && check "tool:$tool" yes "$(command -v "$tool")" ||
        warn "tool:$tool" "not on PATH (only needed for device work or faster rebuilds)"
done

if [[ -d "$ANDROID_HOME" ]]; then
    check android-sdk yes "$ANDROID_HOME"
else
    warn android-sdk "ANDROID_HOME=$ANDROID_HOME missing (needed only for 'apps')"
fi

java_bin="${SABLE_JDK:+$SABLE_JDK/bin/}java"
if command -v "$java_bin" >/dev/null 2>&1; then
    check jdk yes "$("$java_bin" -version 2>&1 | grep -m1 ' version ')"
else
    warn jdk "no JDK found (needed only for 'apps'; JDK 17 or 21)"
fi

if [[ -f "$SABLE_ANDROID_ROOT/build/envsetup.sh" ]]; then
    check android-tree yes "$SABLE_ANDROID_ROOT"
else
    warn android-tree "not synced yet at $SABLE_ANDROID_ROOT (run bootstrap)"
fi

if [[ -d "$SABLE_REPO_ROOT/sable-src" ]]; then
    check sable-src yes "$(head -n1 "$SABLE_REPO_ROOT/sable-src/SOURCE_IMPORT.txt" 2>/dev/null)"
else
    warn sable-src "not imported (Q1 builds don't need it; see apps/README.md)"
fi

echo "DOCTOR=$([[ $status == 0 ]] && echo PASS || echo FAIL)"
exit "$status"
