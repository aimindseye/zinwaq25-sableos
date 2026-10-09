#!/usr/bin/env bash
# Static checks for this repository. No network, no Android tree, no device.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
fails=0
pass() { printf 'PASS  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*"; fails=$((fails + 1)); }

mapfile -t shells < <(git ls-files '*.sh' 2>/dev/null || find . -name '*.sh' -not -path './sable-src/*')

# 1. Shell syntax and shellcheck.
for f in "${shells[@]}"; do
    bash -n "$f" || fail "bash -n $f"
done
if command -v shellcheck >/dev/null 2>&1; then
    if shellcheck -x -P SCRIPTDIR -P "$ROOT" "${shells[@]}"; then pass "shellcheck (${#shells[@]} files)"; else fail shellcheck; fi
else
    printf 'SKIP  shellcheck not installed\n'
fi

# 2. Local manifest is well-formed XML.
if command -v xmllint >/dev/null 2>&1; then
    xmllint --noout manifests/local_manifests/sable_q25.xml && pass "manifest XML" || fail "manifest XML"
else
    python3 -c 'import sys,xml.dom.minidom as m; m.parse(sys.argv[1])' manifests/local_manifests/sable_q25.xml &&
        pass "manifest XML" || fail "manifest XML"
fi

# 3. Pins in q25.env match the local manifest.
# shellcheck source=build/config/q25.env
source build/config/q25.env
check_pin() {
    local path="$1" rev="$2"
    if grep -A4 "path=\"$path\"" manifests/local_manifests/sable_q25.xml | grep -q "revision=\"$rev\""; then
        pass "pin $path"
    else
        fail "pin $path: manifest does not carry $rev"
    fi
}
check_pin device/xelex/Q25 "$SABLE_Q25_DEVICE_TREE_REV"
check_pin kernel/xelex/mt6789 "$SABLE_Q25_KERNEL_REV"
check_pin hardware/mediatek "$SABLE_Q25_HARDWARE_MEDIATEK_REV"
check_pin device/mediatek/sepolicy_vndr "$SABLE_Q25_SEPOLICY_VNDR_REV"
check_pin vendor/xelex/Q25 "$SABLE_Q25_VENDOR_BLOBS_REV"

# 4. apps.tsv: 9 columns, unique module names, known enums.
if awk -F'\t' '
    /^#/ || NF == 0 { next }
    $1 == "module" { next }
    NF != 9 { print "bad column count line " NR; bad = 1 }
    $2 != "platform" && $2 != "common" { print "bad group line " NR; bad = 1 }
    $7 != "unsigned" && $7 != "presigned" { print "bad signing line " NR; bad = 1 }
    $9 != "yes" && $9 != "no" { print "bad enabled line " NR; bad = 1 }
    seen[$1]++ { print "duplicate module " $1; bad = 1 }
    END { exit bad }
' product/q25/apps.tsv; then pass "apps.tsv"; else fail "apps.tsv"; fi

# 5. Nothing that must never be committed.
forbidden="$(git ls-files 2>/dev/null | grep -E '\.(img|zip|apk|jks|keystore|pk8|bin)$|(^|/)proprietary/' | grep -v -e '^\.github/' -e '^sable-src/third_party/pinyin_zh/inputs/' || true)"
if [[ -z "$forbidden" ]]; then pass "no images, archives, APKs or keys tracked"; else fail "forbidden files tracked: $forbidden"; fi

# 6. Stage dry run against a fake Android tree.
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/android/build" "$tmp/apps/run/prebuilt"
touch "$tmp/android/build/envsetup.sh"
printf 'fake' > "$tmp/apps/run/prebuilt/SableKeyboard.apk"
printf 'fake' > "$tmp/apps/run/prebuilt/SableCalculator.apk"
(cd "$tmp/apps/run/prebuilt" && sha256sum ./*.apk > ../SHA256SUMS.txt)
{
    printf 'module\tgroup\tpackage\tsigning\toverrides\tfilename\tsha256\n'
    printf 'SableKeyboard\tplatform\torg.sableos.titan2.keyboard\tunsigned\t-\tSableKeyboard.apk\tx\n'
    printf 'SableCalculator\tcommon\torg.sableos.calculator\tpresigned\tExactCalculator\tSableCalculator.apk\tx\n'
} > "$tmp/apps/run/manifest.tsv"

if SABLE_ANDROID_ROOT="$tmp/android" bash scripts/stage-product.sh Q2 --apps-dir "$tmp/apps/run" >/dev/null 2>"$tmp/err"; then
    v="$tmp/android/vendor/sable/q25"
    ok=yes
    grep -q 'name: "SableKeyboard"' "$v/Android.bp" || ok=no
    grep -q 'default_dev_cert: true' "$v/Android.bp" || ok=no
    grep -q 'overrides: \["ExactCalculator"\]' "$v/Android.bp" || ok=no
    grep -q 'SableCalculator' "$v/sable-q25-apps.mk" || ok=no
    grep -q 'ro.sable.release=Q2' "$v/release.mk" || ok=no
    grep -q 'vendor/sable/q25/sable-q25.mk' "$tmp/android/vendor/extra/product.mk" || ok=no
    [[ "$ok" == yes ]] && pass "stage Q2 dry run" || fail "stage Q2 dry run: generated files wrong"
else
    fail "stage Q2 dry run: $(cat "$tmp/err")"
fi

if SABLE_ANDROID_ROOT="$tmp/android" bash scripts/stage-product.sh Q1 >/dev/null 2>"$tmp/err" &&
    [[ ! -e "$tmp/android/vendor/extra/product.mk" && ! -e "$tmp/android/vendor/sable/q25" ]]; then
    pass "stage Q1 removes Sable layer"
else
    fail "stage Q1: $(cat "$tmp/err")"
fi

# 7. Entry point refuses flashing and unknown devices.
if bash build/sable.sh q25 Q2 flash >/dev/null 2>&1; then fail "flash should be blocked"; else pass "flash blocked"; fi
if bash build/sable.sh titan2 Q2 doctor >/dev/null 2>&1; then fail "non-q25 device accepted"; else pass "only q25 accepted"; fi

echo
if ((fails)); then echo "CI=FAIL ($fails)"; exit 1; fi
echo "CI=PASS"
