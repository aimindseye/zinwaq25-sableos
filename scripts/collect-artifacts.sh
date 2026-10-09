#!/usr/bin/env bash
# Copy the installable outputs of a build into a dated artifact directory with
# SHA256SUMS. Keep every artifact you flash: the Titan 2 lane lost its control
# image because it wasn't retained (docs/LESSONS_FROM_TITAN2.md).
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

RELEASE="${1:-}"
sable_require_release "$RELEASE"
sable_require_android_root

OUT="$SABLE_ANDROID_ROOT/out/target/product/$SABLE_Q25_DEVICE"
[[ -d "$OUT" ]] || sable_fail "no build output at $OUT"

mapfile -t zips < <(find "$OUT" -maxdepth 1 -name 'lineage-*-Q25.zip' -printf '%T@ %p\n' | sort -n | awk '{print $2}')
((${#zips[@]})) || sable_fail "no lineage-*-Q25.zip in $OUT (did 'build' finish?)"
zip="${zips[-1]}"

date_tag="$(date -u +%Y%m%d)"
variant="$SABLE_Q25_BUILD_VARIANT"
dest="$SABLE_ARTIFACTS_ROOT/q25/$RELEASE/$(sable_utc_stamp)"
mkdir -p "$dest"

if sable_release_has_sable_layer "$RELEASE"; then
    name="sableos-q25-$RELEASE-$date_tag-$variant"
else
    name="lineage-control-q25-$RELEASE-$date_tag-$variant"
fi

install -m 0644 "$zip" "$dest/$name.zip"
for img in boot dtbo vbmeta vendor_boot; do
    [[ -f "$OUT/$img.img" ]] || sable_fail "missing $OUT/$img.img"
    install -m 0644 "$OUT/$img.img" "$dest/$img.img"
done
if [[ -f "$OUT/super_empty.img" ]]; then
    install -m 0644 "$OUT/super_empty.img" "$dest/super_empty.img"
fi
for prop in system/build.prop system_ext/etc/build.prop vendor/build.prop; do
    if [[ -f "$OUT/$prop" ]]; then
        install -D -m 0644 "$OUT/$prop" "$dest/props/$prop"
    fi
done
if [[ -f "$SABLE_ANDROID_ROOT/vendor/sable/q25/apps-manifest.tsv" ]]; then
    install -m 0644 "$SABLE_ANDROID_ROOT/vendor/sable/q25/apps-manifest.tsv" "$dest/apps-manifest.tsv"
fi

latest_manifest="$(find "$SABLE_EVIDENCE_ROOT" -maxdepth 1 -name 'manifest-*.xml' -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -n1 | awk '{print $2}')"
if [[ -n "$latest_manifest" ]]; then
    install -m 0644 "$latest_manifest" "$dest/pinned-manifest.xml"
fi

{
    echo "release=$RELEASE"
    echo "lunch=$(sable_lunch_target)"
    echo "tool_commit=$(git -C "$SABLE_REPO_ROOT" rev-parse --verify -q HEAD 2>/dev/null || echo uncommitted)"
    echo "source_zip=$(basename "$zip")"
    echo "boot_tested=NO"
} > "$dest/BUILD_INFO.txt"

sums="$(cd "$dest" && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum)"
printf '%s\n' "$sums" > "$dest/SHA256SUMS.txt"

echo "ARTIFACTS=PASS"
echo "DEST=$dest"
cat "$dest/SHA256SUMS.txt"
