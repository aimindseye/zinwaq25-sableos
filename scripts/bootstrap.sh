#!/usr/bin/env bash
# Initialise and sync the LineageOS 23.2 tree with the Q25 local manifest.
#
# Adapted from the SableOS Panther bootstrap: network access must be explicitly
# authorised, and the exact synced revisions are written to the evidence
# directory.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

if [[ "${NETWORK_FETCH_AUTHORIZED:-NO}" != YES ]]; then
    sable_fail "refusing network sync; set NETWORK_FETCH_AUTHORIZED=YES (about 200 GB download)"
fi

command -v "$SABLE_REPO_TOOL" >/dev/null 2>&1 || sable_fail "repo tool not found ($SABLE_REPO_TOOL)"

mkdir -p "$SABLE_ANDROID_ROOT" "$SABLE_EVIDENCE_ROOT"
cd "$SABLE_ANDROID_ROOT"

if [[ ! -d .repo ]]; then
    sable_log "repo init $SABLE_Q25_LINEAGE_MANIFEST_URL -b $SABLE_Q25_LINEAGE_BRANCH"
    "$SABLE_REPO_TOOL" init \
        -u "$SABLE_Q25_LINEAGE_MANIFEST_URL" \
        -b "$SABLE_Q25_LINEAGE_BRANCH" \
        --git-lfs \
        --no-clone-bundle
fi

mkdir -p .repo/local_manifests
install -m 0644 "$SABLE_REPO_ROOT/manifests/local_manifests/sable_q25.xml" .repo/local_manifests/sable_q25.xml

# A roomservice.xml from an earlier `breakfast Q25` would duplicate our projects.
if [[ -f .repo/local_manifests/roomservice.xml ]]; then
    sable_fail ".repo/local_manifests/roomservice.xml exists; remove it (sable_q25.xml replaces it)"
fi

sable_log "repo sync -j$SABLE_JOBS"
"$SABLE_REPO_TOOL" sync -c -j"$SABLE_JOBS" --no-tags --optimized-fetch --force-sync

stamp="$(sable_utc_stamp)"
pinned="$SABLE_EVIDENCE_ROOT/manifest-$SABLE_Q25_LINEAGE_BRANCH-$stamp.xml"
"$SABLE_REPO_TOOL" manifest -r -o "$pinned"
sha256sum "$pinned"

for path in device/xelex/Q25 kernel/xelex/mt6789 hardware/mediatek device/mediatek/sepolicy_vndr vendor/xelex/Q25; do
    [[ -d "$path" ]] || sable_fail "expected project missing after sync: $path"
done

echo "BOOTSTRAP=PASS"
echo "ANDROID_ROOT=$SABLE_ANDROID_ROOT"
echo "PINNED_MANIFEST=$pinned"
echo "TARGET_RELEASE=$(sable_target_release)"
