#!/usr/bin/env bash
# Fetch and verify the microG and Aurora Store APKs pinned in
# product/q25/microg/prebuilts.tsv (no APK is tracked in git).
#
#   bash scripts/fetch-microg.sh             download what is missing, verify all
#   bash scripts/fetch-microg.sh --check     verify only, never download
#   bash scripts/fetch-microg.sh --from DIR  take APKs downloaded by hand from DIR
#                                            (named <module>.apk or as in the url)
#
# Downloads need NETWORK_FETCH_AUTHORIZED=YES. Each APK lands in
# SABLE_MICROG_CACHE/<module>.apk only after its sha256 matched the pin; a
# mismatching file is deleted, never kept. scripts/stage-product.sh runs --check
# before it stages them (SABLE_Q25_MICROG=YES, Q4 and later).
#
# SABLE_MICROG_PREBUILTS points at another prebuilts.tsv (tests use it).
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

MODE=fetch
FROM=""
while (($#)); do
    case "$1" in
        --check) MODE=check; shift ;;
        --from) [[ $# -ge 2 ]] || sable_fail "--from needs a directory"; FROM="$2"; shift 2 ;;
        -h|--help) sed -n '2,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) sable_fail "unknown argument $1" ;;
    esac
done

TSV="${SABLE_MICROG_PREBUILTS:-$SABLE_REPO_ROOT/product/q25/microg/prebuilts.tsv}"
CACHE="$SABLE_MICROG_CACHE"
[[ -f "$TSV" ]] || sable_fail "no $TSV"
[[ -z "$FROM" || -d "$FROM" ]] || sable_fail "--from $FROM is not a directory"

sha_of() { sha256sum "$1" | awk '{print $1}'; }

# Puts $1 at $CACHE/<module>.apk if its sha256 is $2, otherwise deletes it.
accept() {
    local file="$1" want="$2" module="$3" got
    got="$(sha_of "$file")"
    if [[ "$got" != "$want" ]]; then
        rm -f "$file"
        sable_fail "$module: sha256 $got, pinned $want (file deleted)"
    fi
    mv -f "$file" "$CACHE/$module.apk"
    return 0
}

count=0
missing=""
while IFS=$'\t' read -r module package version sha url; do
    [[ -z "$module" || "$module" == \#* || "$module" == module ]] && continue
    [[ "$sha" =~ ^[0-9a-f]{64}$ ]] || sable_fail "$module: sha256 in $TSV is not 64 hex digits (fill it in first)"
    [[ "$url" == https://* ]] || sable_fail "$module: url must be https"
    target="$CACHE/$module.apk"
    if [[ -f "$target" && "$(sha_of "$target")" == "$sha" ]]; then
        count=$((count + 1))
        continue
    fi
    if [[ "$MODE" == check ]]; then
        missing="$missing $module"
        continue
    fi
    mkdir -p "$CACHE"
    rm -f "$target"
    part="$CACHE/.$module.apk.part"
    if [[ -n "$FROM" ]]; then
        src=""
        for cand in "$FROM/$module.apk" "$FROM/$(basename "$url")"; do
            [[ -f "$cand" ]] && { src="$cand"; break; }
        done
        [[ -n "$src" ]] || sable_fail "$module: neither $module.apk nor $(basename "$url") in $FROM"
        cp "$src" "$part"
    else
        [[ "${NETWORK_FETCH_AUTHORIZED:-NO}" == YES ]] ||
            sable_fail "$module not in $CACHE; set NETWORK_FETCH_AUTHORIZED=YES to download $url"
        sable_log "downloading $module $version"
        curl -fsSL --retry 3 --proto '=https' --tlsv1.2 -o "$part" "$url" ||
            { rm -f "$part"; sable_fail "$module: download failed: $url"; }
    fi
    accept "$part" "$sha" "$module"
    printf 'FETCHED  %s %s %s\n' "$module" "$package" "$version"
    count=$((count + 1))
done < "$TSV"

if [[ -n "$missing" ]]; then
    echo "MICROG_PREBUILTS=MISSING$missing"
    echo "MICROG_CACHE=$CACHE"
    sable_fail "microG APKs missing or not matching their pins:$missing (run: NETWORK_FETCH_AUTHORIZED=YES bash scripts/fetch-microg.sh)"
fi
echo "MICROG_PREBUILTS=PASS"
echo "MICROG_CACHE=$CACHE"
echo "MICROG_APK_COUNT=$count"
