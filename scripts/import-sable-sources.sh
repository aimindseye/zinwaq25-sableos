#!/usr/bin/env bash
# Copy the Sable application sources this port builds from a local checkout of
# aimindseye/sableos into sable-src/, preserving the sableos directory layout so
# the Gradle projects' relative paths keep working unchanged.
#
# sableos is private and this repository is public. Copying publishes the code
# once it is committed and pushed, so the copy needs --authorize-public-copy.
set -euo pipefail

# shellcheck source=build/lib/common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build/lib/common.sh"

usage() {
    cat >&2 <<'EOF'
usage: scripts/import-sable-sources.sh --sableos PATH [--commit SHA] --authorize-public-copy

  --sableos PATH            local clone of aimindseye/sableos
  --commit SHA              commit to import (default: SABLE_Q25_SABLEOS_COMMIT in build/config/q25.env)
  --authorize-public-copy   confirm the owner approved publishing these sources
EOF
}

SABLEOS=""
COMMIT="$SABLE_Q25_SABLEOS_COMMIT"
AUTHORIZED=NO
while (($#)); do
    case "$1" in
        --sableos) SABLEOS="${2:-}"; shift 2 ;;
        --commit) COMMIT="${2:-}"; shift 2 ;;
        --authorize-public-copy) AUTHORIZED=YES; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage; exit 64 ;;
    esac
done

[[ -n "$SABLEOS" ]] || { usage; exit 64; }
[[ "$AUTHORIZED" == YES ]] || sable_fail "pass --authorize-public-copy only after the owner approved publishing sableos sources"
git -C "$SABLEOS" cat-file -e "$COMMIT^{commit}" 2>/dev/null || sable_fail "commit $COMMIT not found in $SABLEOS"

# Paths the Q25 app build needs. Keep in sync with product/q25/apps.tsv.
PATHS=(
    apps/titan2/platform
    apps/r8/android
    apps/r8/rust
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

# Never import build outputs or local signing material.
find "$DEST" \( -name build -o -name .gradle -o -name '*.jks' -o -name '*.keystore' -o -name local.properties \) -prune -exec rm -rf {} +

echo "IMPORT=PASS"
echo "SABLEOS_COMMIT=$COMMIT"
echo "DEST=$DEST"
