#!/usr/bin/env bash
# Fetch and verify the Android Auto stub pinned in product/q25/androidauto/androidauto.tsv
# (SABLE_Q25_ANDROID_AUTO=YES). No APK is tracked in git.
#
#   bash scripts/fetch-android-auto.sh             download what is missing, verify all
#   bash scripts/fetch-android-auto.sh --check     verify only, never download
#   bash scripts/fetch-android-auto.sh --from ZIP  take the NikGapps add-on zip downloaded by hand
#
# Downloads need NETWORK_FETCH_AUTHORIZED=YES. The add-on zip must match its pinned sha256, and
# the stub APK inside it must match its own; a file that does not is deleted, never kept. The
# APK lands in SABLE_MICROG_CACHE/AndroidAutoStub.apk. scripts/stage-product.sh runs --check.
#
# SABLE_ANDROID_AUTO_TSV points at another androidauto.tsv (tests use it).
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

MODE=fetch
FROM=""
while (($#)); do
    case "$1" in
        --check) MODE=check; shift ;;
        --from) [[ $# -ge 2 ]] || sable_fail "--from needs a zip file"; FROM="$2"; shift 2 ;;
        -h|--help) sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) sable_fail "unknown argument $1" ;;
    esac
done

TSV="${SABLE_ANDROID_AUTO_TSV:-$SABLE_REPO_ROOT/product/q25/androidauto/androidauto.tsv}"
CACHE="$SABLE_MICROG_CACHE"
TARGET="$CACHE/AndroidAutoStub.apk"
[[ -f "$TSV" ]] || sable_fail "no $TSV"
[[ -z "$FROM" || -f "$FROM" ]] || sable_fail "--from $FROM is not a file"

sha_of() { sha256sum "$1" | awk '{print $1}'; }

row="$(grep -v '^#' "$TSV" | grep -v '^package' | head -1 || true)"
IFS=$'\t' read -r package zip_sha apk_sha url <<<"$row"
[[ "$zip_sha" =~ ^[0-9a-f]{64}$ && "$apk_sha" =~ ^[0-9a-f]{64}$ ]] || sable_fail "hashes in $TSV are not 64 hex digits"
[[ "$url" == https://* ]] || sable_fail "url must be https"

if [[ -f "$TARGET" && "$(sha_of "$TARGET")" == "$apk_sha" ]]; then
    echo "ANDROID_AUTO=PASS"
    echo "ANDROID_AUTO_APK=$TARGET"
    exit 0
fi
if [[ "$MODE" == check ]]; then
    echo "ANDROID_AUTO=MISSING"
    sable_fail "Android Auto stub missing or not matching its pin (run: NETWORK_FETCH_AUTHORIZED=YES bash scripts/fetch-android-auto.sh)"
fi

command -v unzip >/dev/null || sable_fail "unzip is required"
mkdir -p "$CACHE"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
zip="$work/addon.zip"
if [[ -n "$FROM" ]]; then
    cp "$FROM" "$zip"
else
    [[ "${NETWORK_FETCH_AUTHORIZED:-NO}" == YES ]] ||
        sable_fail "stub not in $CACHE; set NETWORK_FETCH_AUTHORIZED=YES to download $url"
    sable_log "downloading the NikGapps Android Auto add-on"
    curl -fsSL --retry 3 --proto '=https' --tlsv1.2 -o "$zip" "$url" || sable_fail "download failed: $url"
fi
[[ "$(sha_of "$zip")" == "$zip_sha" ]] || sable_fail "add-on zip sha256 $(sha_of "$zip"), pinned $zip_sha (file deleted)"
unzip -q -o "$zip" 'AppSet/AndroidAuto/AndroidAuto.zip' -d "$work/outer"
unzip -q -o "$work/outer/AppSet/AndroidAuto/AndroidAuto.zip" \
    '___priv-app___AndroidAutoStubPrebuilt/AndroidAutoStubPrebuilt.apk' -d "$work/inner"
apk="$work/inner/___priv-app___AndroidAutoStubPrebuilt/AndroidAutoStubPrebuilt.apk"
[[ "$(sha_of "$apk")" == "$apk_sha" ]] || sable_fail "stub sha256 $(sha_of "$apk"), pinned $apk_sha"
install -m 0644 "$apk" "$TARGET"
printf 'FETCHED  AndroidAutoStub %s\n' "$package"
echo "ANDROID_AUTO=PASS"
echo "ANDROID_AUTO_APK=$TARGET"
