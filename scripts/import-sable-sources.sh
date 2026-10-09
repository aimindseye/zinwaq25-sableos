#!/usr/bin/env bash
# Copy the Sable application sources this port builds from a local checkout of
# aimindseye/sableos into sable-src/, preserving the sableos directory layout so
# the Gradle projects' relative paths keep working unchanged.
#
# Then, optionally, layer newer product source from aimindseye/titan2-temp
# (the C3B product-source lane) over it, and finally apply the Q25's own
# changes from patches/sable-src/*.patch in name order.
#
# sableos is private and this repository is public. Copying publishes the code
# once it is committed and pushed, so the copy needs --authorize-public-copy.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

usage() {
    cat >&2 <<'EOF'
usage: scripts/import-sable-sources.sh --sableos PATH [--commit SHA]
                                      [--titan2-temp PATH [--titan2-commit SHA]]
                                      --authorize-public-copy

  --sableos PATH            local clone of aimindseye/sableos
  --commit SHA              commit to import (default: SABLE_Q25_SABLEOS_COMMIT in build/config/q25.env)
  --titan2-temp PATH        local clone of aimindseye/titan2-temp to layer over sableos
  --titan2-commit SHA       its commit (default: SABLE_Q25_TITAN2_TEMP_COMMIT)
  --authorize-public-copy   confirm the owner approved publishing these sources
EOF
}

SABLEOS=""
COMMIT="$SABLE_Q25_SABLEOS_COMMIT"
TITAN=""
TITAN_COMMIT="${SABLE_Q25_TITAN2_TEMP_COMMIT:-}"
AUTHORIZED=NO
while (($#)); do
    case "$1" in
        --sableos) SABLEOS="${2:-}"; shift 2 ;;
        --commit) COMMIT="${2:-}"; shift 2 ;;
        --titan2-temp) TITAN="${2:-}"; shift 2 ;;
        --titan2-commit) TITAN_COMMIT="${2:-}"; shift 2 ;;
        --authorize-public-copy) AUTHORIZED=YES; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage; exit 64 ;;
    esac
done

[[ -n "$SABLEOS" ]] || { usage; exit 64; }
[[ "$AUTHORIZED" == YES ]] || sable_fail "pass --authorize-public-copy only after the owner approved publishing sableos sources"
git -C "$SABLEOS" cat-file -e "$COMMIT^{commit}" 2>/dev/null || sable_fail "commit $COMMIT not found in $SABLEOS"
if [[ -n "$TITAN" ]]; then
    git -C "$TITAN" cat-file -e "$TITAN_COMMIT^{commit}" 2>/dev/null || sable_fail "commit $TITAN_COMMIT not found in $TITAN"
fi

# Paths the Q25 app build needs. Keep in sync with product/q25/apps.tsv.
PATHS=(
    apps/titan2/platform
    apps/r8/android
    apps/r8/rust
    apps/r8/mail
    apps/r8/textreader
    src/android/shared/sabledesign
    src/android/packages/apps/SableStart
    config/detekt.yml
    config/DETEKT_PROVENANCE.env
    config/QUALITY_SUPPRESSION_BUDGET.tsv
    third_party/phosphor-icons
    third_party/pinyin_zh
    tools/gen_first_party_icons.py
    tools/gen_indic_rows.py
    tools/gen_phonetic.py
    tools/gen_pinyin_dict.py
    tools/gen_pinyin_license_bundle.py
    tools/indic_common.py
)

present=()
for p in "${PATHS[@]}"; do
    if git -C "$SABLEOS" cat-file -e "$COMMIT:$p" 2>/dev/null; then
        present+=("$p")
    else
        sable_log "skip (absent at $COMMIT): $p"
    fi
done

DEST="$SABLE_REPO_ROOT/sable-src"
rm -rf "$DEST"
mkdir -p "$DEST"
git -C "$SABLEOS" archive "$COMMIT" -- "${present[@]}" | tar -x -C "$DEST"

{
    echo "sableos $COMMIT"
    echo "imported $(sable_utc_stamp)"
    printf 'path %s\n' "${present[@]}"
} > "$DEST/SOURCE_IMPORT.txt"

# titan2-temp layer: whole directories replace their sableos counterparts.
# Reader v2 (apps/common/reader) supersedes the older apps/r8/android/reader.
TITAN_PATHS=(
    apps/titan2/platform
    apps/common/reader
    reference
)
if [[ -n "$TITAN" ]]; then
    for p in "${TITAN_PATHS[@]}"; do
        git -C "$TITAN" cat-file -e "$TITAN_COMMIT:$p" 2>/dev/null || sable_fail "titan2-temp $TITAN_COMMIT has no $p"
        rm -rf "${DEST:?}/$p"
    done
    git -C "$TITAN" archive "$TITAN_COMMIT" -- "${TITAN_PATHS[@]}" | tar -x -C "$DEST"
    rm -rf "$DEST/apps/r8/android/reader"
    {
        echo "titan2-temp $TITAN_COMMIT"
        printf 'overlay %s\n' "${TITAN_PATHS[@]}"
        echo "removed apps/r8/android/reader (superseded by apps/common/reader)"
    } >> "$DEST/SOURCE_IMPORT.txt"
fi

# Files never published, with the reason. Paths are relative to sable-src/.
EXCLUDE=(
    # Not a font: a saved GitHub web page that embeds the owner's GitHub
    # account details. Unreferenced by the Reader build.
    apps/r8/android/reader/leisure/src/main/res/font/accessible_dfa_vf.ttf
)
for p in "${EXCLUDE[@]}"; do
    rm -f "$DEST/$p"
done

# A last guard against saved web pages or account data sneaking in.
if grep -rIl -e 'csrf_tokens' -e '"userEmail"' "$DEST" >/dev/null 2>&1; then
    grep -rIl -e 'csrf_tokens' -e '"userEmail"' "$DEST" >&2
    sable_fail "imported files contain web-page account data; add them to EXCLUDE"
fi

# Never import build outputs or local signing material.
find "$DEST" \( -name build -o -name .gradle -o -name '*.jks' -o -name '*.keystore' -o -name local.properties \) -prune -exec rm -rf {} +

# Q25 changes to the imported sources (profile entries), applied in name order.
shopt -s nullglob
for patch_file in "$SABLE_REPO_ROOT"/patches/sable-src/*.patch; do
    patch -d "$SABLE_REPO_ROOT" -p1 --forward --batch --silent < "$patch_file" \
        || sable_fail "patch does not apply: $patch_file"
    echo "patch $(basename "$patch_file")" >> "$DEST/SOURCE_IMPORT.txt"
done
shopt -u nullglob

echo "IMPORT=PASS"
echo "SABLEOS_COMMIT=$COMMIT"
[[ -z "$TITAN" ]] || echo "TITAN2_TEMP_COMMIT=$TITAN_COMMIT"
echo "DEST=$DEST"
